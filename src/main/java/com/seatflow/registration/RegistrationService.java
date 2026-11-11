package com.seatflow.registration;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import com.seatflow.infra.messaging.NotificationEvent;
import com.seatflow.notification.Notification;
import com.seatflow.notification.NotificationRepository;
import com.seatflow.notification.NotificationStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.activity.ActivityService;
import com.seatflow.activity.dto.ActivityResponse;
import com.seatflow.common.BusinessException;
import com.seatflow.config.RegistrationProperties;
import com.seatflow.infra.scheduling.RegistrationExpiryHandler;
import com.seatflow.registration.dto.RegistrationResponse;
import com.seatflow.user.CurrentUser;
import com.seatflow.user.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

/**
 * Registration lifecycle: hold a seat, confirm it, release it.
 *
 * <p>中文批注：这一层的核心只有一条规则——先把状态改对，再动名额，而且每一步都必须是
 * "带条件的 UPDATE 加判断影响行数"。确认、取消、超时回收三个方法会竞争同一条报名记录，
 * 谁的影响行数是 1，谁才有资格继续往下走；影响行数是 0 就说明别人先改成功了。
 */
@Service
@RequiredArgsConstructor
public class RegistrationService implements RegistrationExpiryHandler {

    private static final List<RegistrationStatus> ACTIVE_STATUSES =
            List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED);

    private final RegistrationRepository registrationRepository;
    private final ActivityService activityService;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;
    private final RegistrationProperties registrationProperties;
    private final NotificationRepository notificationRepository;

    @Transactional
    public RegistrationResponse registerActivity(Long activityId) {
        CurrentUser currentUser = currentUserProvider.require();
        if (registrationRepository.existsByActivityIdAndUserIdAndStatusIn(
                activityId, currentUser.id(), ACTIVE_STATUSES)) {
            throw BusinessException.conflict("you already have an active registration");
        }

        int seatsLeft = activityService.incrementRegistered(activityId);

        Registration registration = new Registration();
        registration.setActivityId(activityId);
        registration.setUserId(currentUser.id());
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setConfirmDeadline(
                LocalDateTime.now(clock).plus(registrationProperties.getConfirmTtl()));
        Registration saved = registrationRepository.save(registration);
        enqueuePendingNotification(saved, currentUser.id(), activityId);
        return RegistrationResponse.from(saved, seatsLeft);
    }

    @Transactional
    public RegistrationResponse confirmActivity(Long activityId) {
        CurrentUser currentUser = currentUserProvider.require();
        Registration registration = findActiveRegistration(activityId, currentUser.id());
        LocalDateTime now = LocalDateTime.now(clock);

        int updated = registrationRepository.confirm(
                registration.getId(), RegistrationStatus.CONFIRMED, RegistrationStatus.PENDING, now);
        if (updated == 0) {
            throw confirmFailure(registration.getId());
        }
        return responseOf(registration.getId(), activityId);
    }

    @Transactional
    public RegistrationResponse cancelActivity(Long activityId) {
        CurrentUser currentUser = currentUserProvider.require();
        ActivityResponse activity = activityService.getActivity(activityId);
        LocalDateTime now = LocalDateTime.now(clock);
        // Cancellation follows the same window as registration (product decision):
        // half-open [start, end).
        if (now.isBefore(activity.registrationStart()) || !now.isBefore(activity.registrationEnd())) {
            throw BusinessException.conflict("cancellation is only allowed while registration is open");
        }
        Registration registration = findActiveRegistration(activityId, currentUser.id());

        int updated = registrationRepository.transition(
                registration.getId(), RegistrationStatus.CANCELLED, ACTIVE_STATUSES);
        if (updated == 0) {
            throw BusinessException.conflict("your registration is no longer active");
        }
        // Only the caller whose state transition won may release the seat.
        int seatsLeft = activityService.releaseRegistered(activityId);
        return RegistrationResponse.from(reload(registration.getId()), seatsLeft);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getMyRegistration() {
        CurrentUser currentUser = currentUserProvider.require();
        return registrationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id())
                .stream()
                .map(registration -> RegistrationResponse.from(
                        registration,
                        activityService.getSeatsLeft(registration.getActivityId())))
                .toList();
    }

    @Override
    @Transactional
    public int expireOverdue(int batchSize) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Registration> overdue = registrationRepository.findByStatusAndConfirmDeadlineBefore(
                RegistrationStatus.PENDING,
                now,
                PageRequest.of(0, Math.max(batchSize, 1)));

        int expired = 0;
        for (Registration registration : overdue) {
            int updated = registrationRepository.transition(
                    registration.getId(),
                    RegistrationStatus.EXPIRED,
                    List.of(RegistrationStatus.PENDING));
            if (updated == 1) {
                activityService.releaseRegistered(registration.getActivityId());
                expired++;
            }
        }
        return expired;
    }

    private Registration findActiveRegistration(Long activityId, Long userId) {
        return registrationRepository
                .findByActivityIdAndUserIdAndStatusIn(activityId, userId, ACTIVE_STATUSES)
                .orElseThrow(() -> BusinessException.notFound("registration not found"));
    }

    private BusinessException confirmFailure(Long registrationId) {
        Registration current = reload(registrationId);
        return switch (current.getStatus()) {
            case CONFIRMED -> BusinessException.conflict("your registration was already confirmed");
            case PENDING -> BusinessException.conflict("the confirmation deadline has passed");
            case EXPIRED -> BusinessException.conflict("your registration has expired");
            case CANCELLED -> BusinessException.conflict("your registration has been cancelled");
        };
    }

    private RegistrationResponse responseOf(Long registrationId, Long activityId) {
        return RegistrationResponse.from(reload(registrationId), activityService.getSeatsLeft(activityId));
    }

    private Registration reload(Long registrationId) {
        return registrationRepository.findById(registrationId)
                .orElseThrow(() -> BusinessException.notFound("registration not found"));
    }

    /**
     * Writes the outbox row inside the same transaction as the registration.
     *
     * <p>中文批注：通知与报名必须同生共死。写在这里而不是写完后另起一个线程发消息，
     * 是为了避免"报名成功但通知永远丢了"；真正投递由 NotificationOutboxDispatcher
     * 扫描 PENDING 行完成。sentAt 只在投递成功后由投递器写入，这里必须留空。
     */
    private void enqueuePendingNotification(Registration registration, Long userId, Long activityId) {
        Notification notification = new Notification();
        notification.setRegistrationId(registration.getId());
        notification.setActivityId(activityId);
        notification.setUserId(userId);
        notification.setEvent(NotificationEvent.REGISTRATION_PENDING);
        notification.setStatus(NotificationStatus.PENDING);
        notificationRepository.save(notification);
    }
}

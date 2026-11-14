package com.seatflow.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.seatflow.activity.ActivityService;
import com.seatflow.activity.ActivityStatus;
import com.seatflow.activity.dto.ActivityResponse;
import com.seatflow.common.BusinessException;
import com.seatflow.config.RegistrationProperties;
import com.seatflow.infra.gate.SeatGate;
import com.seatflow.infra.messaging.NotificationEvent;
import com.seatflow.metrics.SeatFlowMetrics;
import com.seatflow.notification.Notification;
import com.seatflow.notification.NotificationRepository;
import com.seatflow.notification.NotificationStatus;
import com.seatflow.registration.dto.RegistrationResponse;
import com.seatflow.user.CurrentUser;
import com.seatflow.user.CurrentUserProvider;
import com.seatflow.user.UserRole;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);
    private static final Long ACTIVITY_ID = 1L;
    private static final CurrentUser USER = new CurrentUser(9L, "participant", UserRole.PARTICIPANT);

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private ActivityService activityService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SeatGate seatGate;

    @Mock
    private SeatFlowMetrics metrics;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        RegistrationProperties properties = new RegistrationProperties();
        registrationService = new RegistrationService(
                registrationRepository, activityService, fixedClock,
                currentUserProvider, properties, notificationRepository, seatGate, metrics);
    }

    @Test
    void registerCreatesPendingRegistrationAndReturnsSeatsLeft() {
        given(currentUserProvider.require()).willReturn(USER);
        given(registrationRepository.existsByActivityIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                .willReturn(false);
        given(activityService.getSeatsLeft(ACTIVITY_ID)).willReturn(10);
        given(seatGate.tryReserve(ACTIVITY_ID, 10)).willReturn(true);
        given(activityService.incrementRegistered(ACTIVITY_ID)).willReturn(3);
        given(registrationRepository.save(any(Registration.class))).willAnswer(invocation -> {
            Registration saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        RegistrationResponse result = registrationService.registerActivity(ACTIVITY_ID);

        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        then(registrationRepository).should().save(captor.capture());
        Registration saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(saved.getActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(saved.getUserId()).isEqualTo(USER.id());
        assertThat(saved.getConfirmDeadline()).isEqualTo(NOW.plusMinutes(15));
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(result.seatLeft()).isEqualTo(3);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertThat(notification.getRegistrationId()).isEqualTo(10L);
        assertThat(notification.getActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(notification.getUserId()).isEqualTo(USER.id());
        assertThat(notification.getEvent()).isEqualTo(NotificationEvent.REGISTRATION_PENDING);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        // sentAt is written by the dispatcher only after a successful publish.
        assertThat(notification.getSentAt()).isNull();
    }

    @Test
    void registerRejectsDuplicateWithoutReservingAnotherSeat() {
        given(currentUserProvider.require()).willReturn(USER);
        given(registrationRepository.existsByActivityIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                .willReturn(true);

        assertThatThrownBy(() -> registrationService.registerActivity(ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("you already have an active registration");

        then(activityService).shouldHaveNoInteractions();
        then(registrationRepository).should(never()).save(any(Registration.class));
        then(notificationRepository).shouldHaveNoInteractions();
        then(metrics).should().registrationRejected("duplicate");
    }

    @Test
    void registerRejectsWhenFullWithoutCreatingRegistration() {
        given(currentUserProvider.require()).willReturn(USER);
        given(registrationRepository.existsByActivityIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                .willReturn(false);
        given(activityService.getSeatsLeft(ACTIVITY_ID)).willReturn(0);
        given(seatGate.tryReserve(ACTIVITY_ID, 0)).willReturn(true);
        given(activityService.incrementRegistered(ACTIVITY_ID))
                .willThrow(BusinessException.conflict("activity is full"));

        assertThatThrownBy(() -> registrationService.registerActivity(ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("activity is full");

        then(registrationRepository).should(never()).save(any(Registration.class));
        then(notificationRepository).shouldHaveNoInteractions();
    }

    @Test
    void registerRejectsOutsideWindowWithoutCreatingRegistration() {
        given(currentUserProvider.require()).willReturn(USER);
        given(registrationRepository.existsByActivityIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                .willReturn(false);
        given(activityService.getSeatsLeft(ACTIVITY_ID)).willReturn(10);
        given(seatGate.tryReserve(ACTIVITY_ID, 10)).willReturn(true);
        given(activityService.incrementRegistered(ACTIVITY_ID))
                .willThrow(BusinessException.conflict("activity is not open for registration"));

        assertThatThrownBy(() -> registrationService.registerActivity(ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("activity is not open for registration");

        then(registrationRepository).should(never()).save(any(Registration.class));
        then(notificationRepository).shouldHaveNoInteractions();
    }

    @Test
    void confirmMovesPendingToConfirmed() {
        Registration pending = registration(10L, RegistrationStatus.PENDING);
        given(currentUserProvider.require()).willReturn(USER);
        given(registrationRepository.findByActivityIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                .willReturn(Optional.of(pending));
        given(registrationRepository.confirm(
                eq(10L), eq(RegistrationStatus.CONFIRMED),
                eq(RegistrationStatus.PENDING), any(LocalDateTime.class))).willReturn(1);
        given(registrationRepository.findById(10L))
                .willReturn(Optional.of(registration(10L, RegistrationStatus.CONFIRMED)));
        given(activityService.getSeatsLeft(ACTIVITY_ID)).willReturn(2);

        RegistrationResponse result = registrationService.confirmActivity(ACTIVITY_ID);

        assertThat(result.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(result.seatLeft()).isEqualTo(2);

        Notification notification = captureSingleNotification();
        assertThat(notification.getEvent()).isEqualTo(NotificationEvent.REGISTRATION_CONFIRMED);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRegistrationId()).isEqualTo(10L);
    }

    @Test
    void confirmIsIdempotentWhenAlreadyConfirmed() {
        Registration confirmed = registration(10L, RegistrationStatus.CONFIRMED);
        given(currentUserProvider.require()).willReturn(USER);
        given(registrationRepository.findByActivityIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                .willReturn(Optional.of(confirmed));
        given(registrationRepository.confirm(
                eq(10L), eq(RegistrationStatus.CONFIRMED),
                eq(RegistrationStatus.PENDING), any(LocalDateTime.class))).willReturn(0);
        given(registrationRepository.findById(10L)).willReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> registrationService.confirmActivity(ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("your registration was already confirmed");
    }

    @Test
    void cancelMovesPendingToCancelledAndReleasesSeat() {
        Registration pending = registration(10L, RegistrationStatus.PENDING);
        given(currentUserProvider.require()).willReturn(USER);
        given(activityService.getActivity(ACTIVITY_ID)).willReturn(openActivity());
        given(registrationRepository.findByActivityIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                .willReturn(Optional.of(pending));
        given(registrationRepository.transition(eq(10L), eq(RegistrationStatus.CANCELLED), any()))
                .willReturn(1);
        given(activityService.releaseRegistered(ACTIVITY_ID)).willReturn(2);
        given(registrationRepository.findById(10L))
                .willReturn(Optional.of(registration(10L, RegistrationStatus.CANCELLED)));

        RegistrationResponse result = registrationService.cancelActivity(ACTIVITY_ID);

        assertThat(result.status()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(result.seatLeft()).isEqualTo(2);
        then(activityService).should().releaseRegistered(ACTIVITY_ID);

        Notification notification = captureSingleNotification();
        assertThat(notification.getEvent()).isEqualTo(NotificationEvent.REGISTRATION_CANCELLED);
        assertThat(notification.getRegistrationId()).isEqualTo(10L);
    }

    @Test
    void cancelIsIdempotentAndDoesNotReleaseSeatTwice() {
        Registration pending = registration(10L, RegistrationStatus.PENDING);
        given(currentUserProvider.require()).willReturn(USER);
        given(activityService.getActivity(ACTIVITY_ID)).willReturn(openActivity());
        given(registrationRepository.findByActivityIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                .willReturn(Optional.of(pending));
        given(registrationRepository.transition(eq(10L), eq(RegistrationStatus.CANCELLED), any()))
                .willReturn(0);

        assertThatThrownBy(() -> registrationService.cancelActivity(ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("your registration is no longer active");

        then(activityService).should(never()).releaseRegistered(any());
    }

    @Test
    void expireOverdueReleasesSeatForOverduePending() {
        Registration overdue = registration(10L, RegistrationStatus.PENDING);
        overdue.setConfirmDeadline(NOW.minusMinutes(1));
        given(registrationRepository.findByStatusAndConfirmDeadlineBefore(
                eq(RegistrationStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(overdue));
        given(registrationRepository.transition(eq(10L), eq(RegistrationStatus.EXPIRED), any()))
                .willReturn(1);
        given(activityService.releaseRegistered(ACTIVITY_ID)).willReturn(2);

        int expired = registrationService.expireOverdue(100);

        assertThat(expired).isEqualTo(1);
        then(activityService).should().releaseRegistered(ACTIVITY_ID);

        Notification notification = captureSingleNotification();
        assertThat(notification.getEvent()).isEqualTo(NotificationEvent.REGISTRATION_EXPIRED);
        assertThat(notification.getUserId()).isEqualTo(USER.id());
        assertThat(notification.getActivityId()).isEqualTo(ACTIVITY_ID);
    }

    @Test
    void expireOverdueIsIdempotentWhenAlreadyExpired() {
        Registration overdue = registration(10L, RegistrationStatus.PENDING);
        overdue.setConfirmDeadline(NOW.minusMinutes(1));
        given(registrationRepository.findByStatusAndConfirmDeadlineBefore(
                eq(RegistrationStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(overdue));
        given(registrationRepository.transition(eq(10L), eq(RegistrationStatus.EXPIRED), any()))
                .willReturn(0);

        int expired = registrationService.expireOverdue(100);

        assertThat(expired).isZero();
        then(activityService).should(never()).releaseRegistered(any());
    }

    private Registration registration(Long id, RegistrationStatus status) {
        Registration registration = new Registration();
        registration.setId(id);
        registration.setActivityId(ACTIVITY_ID);
        registration.setUserId(USER.id());
        registration.setStatus(status);
        registration.setConfirmDeadline(NOW.plusMinutes(15));
        return registration;
    }

    private ActivityResponse openActivity() {
        return new ActivityResponse(
                ACTIVITY_ID, 7L, "AI meetup", "desc", "Main hall",
                100, 10, 90, true, NOW.minusDays(1), NOW.plusDays(1), ActivityStatus.PUBLISHED);
    }

    private Notification captureSingleNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        return captor.getValue();
    }
}

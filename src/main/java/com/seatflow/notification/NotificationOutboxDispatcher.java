package com.seatflow.notification;

import com.seatflow.infra.messaging.NotificationMessage;
import com.seatflow.infra.messaging.NotificationPublisher;
import com.seatflow.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class NotificationOutboxDispatcher {

    private final NotificationRepository notificationRepository;
    private final NotificationPublisher publisher;
    private final NotificationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public NotificationOutboxDispatcher(NotificationRepository notificationRepository,
                                        NotificationPublisher publisher,
                                        NotificationProperties properties,
                                        Clock clock,
                                        PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${seatflow.notification.dispatch-interval:5s}")
    public void dispatchPending() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Notification> pending =
                notificationRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        NotificationStatus.PENDING,
                        now,
                        PageRequest.of(0, Math.max(properties.getBatchSize(), 1)));

        for (Notification notification : pending) {
            try {
                publisher.publish(toMessage(notification));
                // The update needs its own transaction, and that transaction must
                // not stay open while the broker call above is in flight.
                transactionTemplate.executeWithoutResult(status -> notificationRepository.markSent(
                        notification.getId(),
                        NotificationStatus.SENT,
                        NotificationStatus.PENDING,
                        LocalDateTime.now(clock)));
            } catch (RuntimeException ex) {
                handlePublishFailure(notification, ex);
            }
        }
    }

    /**
     * Bounded exponential backoff. The first failure schedules a retry after
     * baseDelay, then doubles, capped at maxDelay. Once maxAttempts is reached
     * the row becomes FAILED and the dispatcher stops selecting it.
     */
    private void handlePublishFailure(Notification notification, RuntimeException cause) {
        int attempts = notification.getAttempts() + 1;
        String error = truncate(cause.getMessage());

        if (attempts >= properties.getMaxAttempts()) {
            transactionTemplate.executeWithoutResult(status -> notificationRepository.markFailed(
                    notification.getId(),
                    NotificationStatus.FAILED,
                    NotificationStatus.PENDING,
                    attempts,
                    error));
            log.error("notification {} failed permanently after {} attempts",
                    notification.getId(), attempts, cause);
            return;
        }

        LocalDateTime nextAttemptAt = LocalDateTime.now(clock).plus(backoff(attempts));
        transactionTemplate.executeWithoutResult(status -> notificationRepository.scheduleRetry(
                notification.getId(),
                NotificationStatus.PENDING,
                attempts,
                nextAttemptAt,
                error));
        log.warn("notification {} publish failed (attempt {} of {}), next attempt at {}",
                notification.getId(), attempts, properties.getMaxAttempts(), nextAttemptAt);
    }

    private Duration backoff(int attempts) {
        long multiplier = 1L << Math.min(attempts - 1, 20);
        Duration delay = properties.getBaseDelay().multipliedBy(multiplier);
        return delay.compareTo(properties.getMaxDelay()) > 0 ? properties.getMaxDelay() : delay;
    }


    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "unknown publish failure";
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private NotificationMessage toMessage(Notification notification) {
        return new NotificationMessage(
                notification.getId(),
                notification.getRegistrationId(),
                notification.getActivityId(),
                notification.getUserId(),
                notification.getEvent());
    }
}

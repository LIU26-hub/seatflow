package com.seatflow.notification;

import com.seatflow.infra.messaging.NotificationMessage;
import com.seatflow.infra.messaging.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class NotificationOutboxDispatcher {

    private static final int BATCH_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationPublisher publisher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public NotificationOutboxDispatcher(NotificationRepository notificationRepository,
                                        NotificationPublisher publisher,
                                        Clock clock,
                                        PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.publisher = publisher;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${seatflow.notification.dispatch-interval:5s}")
    public void dispatchPending() {
        List<Notification> pending = notificationRepository.findByStatusOrderByIdAsc(
                NotificationStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

        for (Notification notification : pending) {
            try {
                NotificationMessage message = new NotificationMessage(
                        notification.getId(),
                        notification.getRegistrationId(),
                        notification.getActivityId(),
                        notification.getUserId(),
                        notification.getEvent());
                publisher.publish(message);
                // The update needs its own transaction, and that transaction must
                // not stay open while the broker call above is in flight.
                transactionTemplate.executeWithoutResult(status -> notificationRepository.markSent(
                        notification.getId(),
                        NotificationStatus.SENT,
                        NotificationStatus.PENDING,
                        LocalDateTime.now(clock)));
            } catch (RuntimeException ex) {
                log.error("failed to publish notification {}", notification.getId(), ex);
            }
        }
    }
}

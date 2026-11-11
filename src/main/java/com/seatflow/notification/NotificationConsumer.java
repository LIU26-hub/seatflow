package com.seatflow.notification;


import com.seatflow.infra.messaging.NotificationMessage;
import com.seatflow.infra.messaging.RabbitMessagingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @RabbitListener(queues = RabbitMessagingConfig.NOTIFICATION_QUEUE)
    @Transactional
    public void onNotification(NotificationMessage message) {
        int updated = notificationRepository.markDelivered(
                message.notificationId(),
                NotificationStatus.DELIVERED,
                List.of(NotificationStatus.PENDING, NotificationStatus.SENT),
                LocalDateTime.now(clock));

        if (updated == 0) {
            log.info("duplicate notification skipped: {}", message.notificationId());
            return;
        }
        log.info("notification delivered: {}", message.notificationId());
    }
}

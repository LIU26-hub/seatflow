package com.seatflow.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.infra.messaging.NotificationEvent;
import com.seatflow.infra.messaging.NotificationMessage;

/**
 * The consumer must be idempotent: a redelivery of the same message may not
 * change anything once the notification is already DELIVERED.
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        consumer = new NotificationConsumer(notificationRepository, clock);
    }

    @Test
    void marksNotificationDelivered() {
        given(notificationRepository.markDelivered(
                eq(42L), eq(NotificationStatus.DELIVERED),
                eq(List.of(NotificationStatus.PENDING, NotificationStatus.SENT)), eq(NOW)))
                .willReturn(1);

        consumer.onNotification(message());

        then(notificationRepository).should().markDelivered(
                eq(42L), eq(NotificationStatus.DELIVERED),
                eq(List.of(NotificationStatus.PENDING, NotificationStatus.SENT)), eq(NOW));
    }

    @Test
    void ignoresRedeliveryWithoutFailing() {
        given(notificationRepository.markDelivered(
                eq(42L), eq(NotificationStatus.DELIVERED),
                eq(List.of(NotificationStatus.PENDING, NotificationStatus.SENT)), any()))
                .willReturn(0);

        consumer.onNotification(message());

        then(notificationRepository).should().markDelivered(
                eq(42L), eq(NotificationStatus.DELIVERED),
                eq(List.of(NotificationStatus.PENDING, NotificationStatus.SENT)), eq(NOW));
    }

    private NotificationMessage message() {
        return new NotificationMessage(42L, 7L, 1L, 9L, NotificationEvent.REGISTRATION_PENDING);
    }
}

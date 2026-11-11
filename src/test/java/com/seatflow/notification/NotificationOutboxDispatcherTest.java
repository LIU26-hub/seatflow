package com.seatflow.notification;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.seatflow.infra.messaging.NotificationEvent;
import com.seatflow.infra.messaging.NotificationMessage;
import com.seatflow.infra.messaging.NotificationPublisher;

/**
 * The dispatcher owns three responsibilities: read a batch of PENDING rows,
 * publish each one, and mark only the successfully published rows as SENT.
 * These tests pin exactly that, including the failure path.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOutboxDispatcherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPublisher publisher;

    private NotificationOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        // A real TransactionTemplate over a no-op transaction manager keeps the
        // test focused on control flow rather than on Spring plumbing.
        PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        dispatcher = new NotificationOutboxDispatcher(
                notificationRepository, publisher, clock, transactionManager);
    }

    @Test
    void publishesPendingRowsAndMarksThemSent() {
        given(notificationRepository.findByStatusOrderByIdAsc(
                eq(NotificationStatus.PENDING), any(Pageable.class)))
                .willReturn(List.of(notification(1L)));

        dispatcher.dispatchPending();

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        then(publisher).should().publish(message.capture());
        assertThatMessage(message.getValue());

        then(notificationRepository).should().markSent(
                eq(1L), eq(NotificationStatus.SENT), eq(NotificationStatus.PENDING), eq(NOW));
    }

    @Test
    void keepsRowPendingWhenPublishingFails() {
        given(notificationRepository.findByStatusOrderByIdAsc(
                eq(NotificationStatus.PENDING), any(Pageable.class)))
                .willReturn(List.of(notification(1L)));
        org.mockito.BDDMockito.willThrow(new IllegalStateException("broker down"))
                .given(publisher).publish(any(NotificationMessage.class));

        dispatcher.dispatchPending();

        then(notificationRepository).should(never()).markSent(
                anyLong(), any(), any(), any());
    }

    @Test
    void continuesWithNextRowAfterOneFailure() {
        given(notificationRepository.findByStatusOrderByIdAsc(
                eq(NotificationStatus.PENDING), any(Pageable.class)))
                .willReturn(List.of(notification(1L), notification(2L)));
        org.mockito.BDDMockito.willThrow(new IllegalStateException("broker hiccup"))
                .willDoNothing()
                .given(publisher).publish(any(NotificationMessage.class));

        dispatcher.dispatchPending();

        then(publisher).should(org.mockito.Mockito.times(2)).publish(any(NotificationMessage.class));
        then(notificationRepository).should().markSent(
                eq(2L), eq(NotificationStatus.SENT), eq(NotificationStatus.PENDING), eq(NOW));
        then(notificationRepository).should(never()).markSent(
                eq(1L), any(), any(), any());
    }

    private void assertThatMessage(NotificationMessage message) {
        org.assertj.core.api.Assertions.assertThat(message.notificationId()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(message.registrationId()).isEqualTo(11L);
        org.assertj.core.api.Assertions.assertThat(message.activityId()).isEqualTo(21L);
        org.assertj.core.api.Assertions.assertThat(message.userId()).isEqualTo(31L);
        org.assertj.core.api.Assertions.assertThat(message.event())
                .isEqualTo(NotificationEvent.REGISTRATION_PENDING);
    }

    private Notification notification(Long id) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRegistrationId(10L + id);
        notification.setActivityId(20L + id);
        notification.setUserId(30L + id);
        notification.setEvent(NotificationEvent.REGISTRATION_PENDING);
        notification.setStatus(NotificationStatus.PENDING);
        return notification;
    }
}

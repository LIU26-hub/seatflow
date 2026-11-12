package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Duration;
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

import com.seatflow.config.NotificationProperties;
import com.seatflow.infra.messaging.NotificationEvent;
import com.seatflow.infra.messaging.NotificationMessage;
import com.seatflow.infra.messaging.NotificationPublisher;

/**
 * Covers the dispatcher contract: publish only due rows, mark successes SENT,
 * and apply bounded exponential backoff to failures.
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
                notificationRepository, publisher, new NotificationProperties(),
                clock, transactionManager);
    }

    @Test
    void publishesDueRowsAndMarksThemSent() {
        givenDue(notification(1L, 0));

        dispatcher.dispatchPending();

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        then(publisher).should().publish(message.capture());
        assertThat(message.getValue().notificationId()).isEqualTo(1L);
        assertThat(message.getValue().registrationId()).isEqualTo(11L);
        assertThat(message.getValue().activityId()).isEqualTo(21L);
        assertThat(message.getValue().userId()).isEqualTo(31L);
        assertThat(message.getValue().event()).isEqualTo(NotificationEvent.REGISTRATION_PENDING);

        then(notificationRepository).should().markSent(
                eq(1L), eq(NotificationStatus.SENT), eq(NotificationStatus.PENDING), eq(NOW));
    }

    @Test
    void schedulesFirstRetryAfterBaseDelay() {
        givenDue(notification(1L, 0));
        willThrow(new IllegalStateException("broker down"))
                .given(publisher).publish(any(NotificationMessage.class));

        dispatcher.dispatchPending();

        then(notificationRepository).should().scheduleRetry(
                eq(1L), eq(NotificationStatus.PENDING), eq(1),
                eq(NOW.plus(Duration.ofSeconds(5))), eq("broker down"));
        then(notificationRepository).should(never()).markFailed(any(), any(), any(), anyInt(), any());
    }

    @Test
    void doublesTheDelayOnEachFailure() {
        givenDue(notification(1L, 2));
        willThrow(new IllegalStateException("broker down"))
                .given(publisher).publish(any(NotificationMessage.class));

        dispatcher.dispatchPending();

        // Third failure: 5s * 2^2 = 20s.
        then(notificationRepository).should().scheduleRetry(
                eq(1L), eq(NotificationStatus.PENDING), eq(3),
                eq(NOW.plusSeconds(20)), eq("broker down"));
    }

    @Test
    void movesRowToFailedOnceTheRetryBudgetIsExhausted() {
        givenDue(notification(1L, 4));
        willThrow(new IllegalStateException("broker down"))
                .given(publisher).publish(any(NotificationMessage.class));

        dispatcher.dispatchPending();

        then(notificationRepository).should().markFailed(
                eq(1L), eq(NotificationStatus.FAILED), eq(NotificationStatus.PENDING),
                eq(5), eq("broker down"));
        then(notificationRepository).should(never()).scheduleRetry(any(), any(), anyInt(), any(), any());
    }

    @Test
    void continuesWithTheNextRowAfterOneFailure() {
        givenDue(notification(1L, 0), notification(2L, 0));
        willThrow(new IllegalStateException("hiccup"))
                .given(publisher).publish(any(NotificationMessage.class));

        dispatcher.dispatchPending();

        then(publisher).should(org.mockito.Mockito.times(2)).publish(any(NotificationMessage.class));
        then(notificationRepository).should().scheduleRetry(
                eq(1L), eq(NotificationStatus.PENDING), eq(1), any(), eq("hiccup"));
        then(notificationRepository).should().scheduleRetry(
                eq(2L), eq(NotificationStatus.PENDING), eq(1), any(), eq("hiccup"));
    }

    private void givenDue(Notification... notifications) {
        given(notificationRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                eq(NotificationStatus.PENDING), eq(NOW), any(Pageable.class)))
                .willReturn(List.of(notifications));
    }

    private Notification notification(Long id, int attempts) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRegistrationId(10L + id);
        notification.setActivityId(20L + id);
        notification.setUserId(30L + id);
        notification.setEvent(NotificationEvent.REGISTRATION_PENDING);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttempts(attempts);
        notification.setNextAttemptAt(NOW.minusSeconds(1));
        return notification;
    }
}

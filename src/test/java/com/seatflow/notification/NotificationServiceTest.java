package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.infra.messaging.NotificationEvent;
import com.seatflow.notification.dto.NotificationResponse;
import com.seatflow.user.CurrentUser;
import com.seatflow.user.CurrentUserProvider;
import com.seatflow.user.UserRole;

/**
 * The query must be scoped to the caller and must only expose delivered rows.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static final CurrentUser USER = new CurrentUser(9L, "participant", UserRole.PARTICIPANT);

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, currentUserProvider);
    }

    @Test
    void returnsDeliveredNotificationsOfTheCurrentUser() {
        NotificationResponse delivered = new NotificationResponse(
                42L, NotificationEvent.REGISTRATION_PENDING, 7L, NOW,
                NotificationStatus.DELIVERED, 1L, "AI meetup", "Main hall",
                NOW.minusDays(1), NOW.plusDays(1));
        given(currentUserProvider.require()).willReturn(USER);
        given(notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                9L, NotificationStatus.DELIVERED)).willReturn(List.of(delivered));

        List<NotificationResponse> result = notificationService.getMyNotification();

        assertThat(result).containsExactly(delivered);
        then(notificationRepository).should().findByUserIdAndStatusOrderByCreatedAtDesc(
                9L, NotificationStatus.DELIVERED);
    }
}

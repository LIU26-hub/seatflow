package com.seatflow.notification;

import com.seatflow.notification.dto.NotificationResponse;
import com.seatflow.user.CurrentUser;
import com.seatflow.user.CurrentUserProvider;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotification() {
        CurrentUser currentUser = currentUserProvider.require();
        return notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(currentUser.id(), NotificationStatus.DELIVERED);
    }
}

package com.seatflow.notification.dto;

import com.seatflow.infra.messaging.NotificationEvent;
import com.seatflow.notification.Notification;
import com.seatflow.notification.NotificationStatus;

import java.time.LocalDateTime;

public record NotificationResponse(Long id,
                                   NotificationEvent event,
                                   Long registrationId,
                                   LocalDateTime createdAt,
                                   NotificationStatus status,
                                   Long activityId,
                                   String activityTitle,
                                   String activityLocation,
                                   LocalDateTime registrationStart,
                                   LocalDateTime registrationEnd
                                   ) {
}

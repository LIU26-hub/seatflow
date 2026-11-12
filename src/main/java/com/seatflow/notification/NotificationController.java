package com.seatflow.notification;

import com.seatflow.common.ApiResponse;
import com.seatflow.notification.dto.NotificationResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {


    private final NotificationService notificationService;

    @GetMapping("/mine")
    public ApiResponse<List<NotificationResponse>> getMyNotification() {
        return ApiResponse.success(notificationService.getMyNotification());
    }

}

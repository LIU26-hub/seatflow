package com.seatflow.registration.dto;

import com.seatflow.registration.Registration;
import com.seatflow.registration.RegistrationStatus;

import java.time.LocalDateTime;

public record RegistrationResponse (
        Long id,
        Long activityId,
        String activityTitle,
        RegistrationStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime confirmDeadline,
        int seatLeft

) {
    public static RegistrationResponse from(Registration registration, int seatLeft, String activityTitle) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getActivityId(),
                activityTitle,
                registration.getStatus(),
                registration.getConfirmedAt(),
                registration.getConfirmDeadline(),
                seatLeft
        );
    }
}
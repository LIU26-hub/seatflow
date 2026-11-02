package com.seatflow.registration.dto;

import com.seatflow.activity.Activity;
import com.seatflow.registration.Registration;
import com.seatflow.registration.RegistrationStatus;

import java.time.LocalDateTime;

public record RegistrationResponse (
        Long id,
        RegistrationStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime confirmDeadline,
        int seatLeft

) {
    public static RegistrationResponse from(Registration registration,int seatLeft) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getStatus(),
                registration.getConfirmedAt(),
                registration.getConfirmDeadline(),
                seatLeft
        );
    }
}

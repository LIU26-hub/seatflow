package com.seatflow.registration.dto;

import com.seatflow.registration.Registration;
import com.seatflow.registration.RegistrationStatus;
import com.seatflow.user.AppUser;

import java.time.LocalDateTime;

/**
 * 组织者视角的报名名单行：报名记录 + 报名人身份信息，供名单展示与 CSV 导出。
 */
public record ActivityRegistrationResponse(
        Long registrationId,
        Long userId,
        String username,
        String displayName,
        RegistrationStatus status,
        LocalDateTime createdAt,
        LocalDateTime confirmDeadline,
        LocalDateTime confirmedAt) {

    public static ActivityRegistrationResponse from(Registration registration, AppUser user) {
        return new ActivityRegistrationResponse(
                registration.getId(),
                registration.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getDisplayName(),
                registration.getStatus(),
                registration.getCreatedAt(),
                registration.getConfirmDeadline(),
                registration.getConfirmedAt());
    }
}
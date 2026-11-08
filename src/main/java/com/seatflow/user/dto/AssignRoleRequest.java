package com.seatflow.user.dto;

import com.seatflow.user.UserRole;

import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(@NotNull UserRole role) {
}

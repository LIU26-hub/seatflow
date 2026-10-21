package com.seatflow.user.dto;

import com.seatflow.security.CurrentUser;

public record AuthResponse(String token,
                           Long userId,
                           String username,
                           String role) {
    public static AuthResponse from(String token, CurrentUser user) {
        return new AuthResponse(token, user.id(), user.username(), user.role().name());
    }
}

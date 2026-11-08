package com.seatflow.user.dto;

import com.seatflow.user.AppUser;
import com.seatflow.user.UserRole;

public record UserResponse(Long id, String username, UserRole role) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}

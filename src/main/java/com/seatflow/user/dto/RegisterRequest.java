package com.seatflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank@Size(min=3,max=20) String username,
        @NotBlank@Size(min=6,max=20) String password,
        @NotBlank@Size(min=3,max=20) String displayName) {

}

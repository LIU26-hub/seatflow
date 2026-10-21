package com.seatflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank @Size String password) {

}

package com.seatflow.qa.dto;

import jakarta.validation.constraints.NotBlank;

public record QaRequest(
        @NotBlank String question,
        Long activityId) {
}

package com.seatflow.qa.dto;

import java.util.List;

public record QaResponse(
        String answer,
        List<QaCitation> citations,
        long latencyMs) {
}

package com.seatflow.activity.dto;

import java.time.LocalDateTime;

import com.seatflow.activity.Activity;
import com.seatflow.activity.ActivityStatus;

/**
 * What the API exposes for an activity.
 *
 * <p>The entity is never returned directly: the wire format is a deliberate
 * contract, and it carries a derived field ({@code seatsLeft}) that does not
 * exist in the database.
 */
public record ActivityResponse(
        Long id,
        String title,
        String description,
        String location,
        int capacity,
        int registeredCount,
        int seatsLeft,
        LocalDateTime registrationStart,
        LocalDateTime registrationEnd,
        ActivityStatus status) {

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getTitle(),
                activity.getDescription(),
                activity.getLocation(),
                activity.getCapacity(),
                activity.getRegisteredCount(),
                activity.seatsLeft(),
                activity.getRegistrationStart(),
                activity.getRegistrationEnd(),
                activity.getStatus());
    }
}

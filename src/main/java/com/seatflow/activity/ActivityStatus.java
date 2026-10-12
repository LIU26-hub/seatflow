package com.seatflow.activity;

/**
 * Lifecycle of an activity.
 *
 * <p>Stored as a string in the database ({@code @Enumerated(EnumType.STRING)})
 * so that adding a new value never renumbers existing rows.
 */
public enum ActivityStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    FINISHED
}

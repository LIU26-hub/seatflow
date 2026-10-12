package com.seatflow.common;

/**
 * Single response envelope for every endpoint.
 *
 * <p>A record is used on purpose: it is immutable, it removes getter and setter
 * boilerplate, and it makes the shape of the payload explicit at the type level.
 */
public record ApiResponse<T>(int code, String message, T data) {

    private static final int OK = 0;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(OK, "ok", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(OK, "ok", null);
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}

package com.seatflow.infra.ratelimit;

/**
 * Outcome of one rate limit check.
 *
 * @param allowed           whether the request may proceed
 * @param retryAfterSeconds how long the caller should wait, 0 when allowed
 * @param scope             which limit was hit, {@code null} when allowed
 */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds, String scope) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, 0, null);
    }

    public static RateLimitDecision deny(String scope, long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(retryAfterSeconds, 1), scope);
    }
}

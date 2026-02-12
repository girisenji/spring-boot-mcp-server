package io.github.girisenji.ai.model;

import java.time.Duration;

/**
 * Configuration for rate limiting a specific tool.
 * 
 * @param maxRequests Maximum number of requests allowed
 * @param window      Time window for the rate limit (ISO-8601 duration)
 */
public record RateLimitConfig(
        int maxRequests,
        Duration window) {

    public RateLimitConfig {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be a positive duration");
        }
    }

    /**
     * Parse rate limit configuration from YAML-friendly format.
     * 
     * @param requests Number of requests allowed
     * @param window   ISO-8601 duration string (e.g., "PT1H" for 1 hour, "PT1M" for
     *                 1 minute)
     * @return parsed RateLimitConfig
     * @throws IllegalArgumentException if window format is invalid or requests is
     *                                  non-positive
     */
    public static RateLimitConfig parse(int requests, String window) {
        if (window == null || window.isBlank()) {
            throw new IllegalArgumentException("Rate limit window must not be null or blank");
        }
        try {
            return new RateLimitConfig(requests, Duration.parse(window));
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid ISO-8601 duration format for rate limit window: '" + window +
                            "'. Examples: PT1H (1 hour), PT30M (30 minutes), PT1M (1 minute)",
                    e);
        }
    }
}

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
     */
    public static RateLimitConfig parse(int requests, String window) {
        return new RateLimitConfig(requests, Duration.parse(window));
    }
}

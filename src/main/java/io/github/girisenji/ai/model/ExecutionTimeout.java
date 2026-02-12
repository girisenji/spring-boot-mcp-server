package io.github.girisenji.ai.model;

import java.time.Duration;

/**
 * Configuration for tool execution timeouts.
 * 
 * @param timeout Timeout duration for HTTP request execution (read timeout)
 */
public record ExecutionTimeout(Duration timeout) {

    public ExecutionTimeout {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Execution timeout must be a positive duration");
        }
    }

    /**
     * Parse execution timeout from YAML-friendly format.
     * 
     * @param timeout ISO-8601 duration string (e.g., "PT30S" for 30 seconds, "PT5M"
     *                for 5 minutes)
     * @return parsed ExecutionTimeout
     * @throws IllegalArgumentException if timeout format is invalid
     */
    public static ExecutionTimeout parse(String timeout) {
        if (timeout == null || timeout.isBlank()) {
            throw new IllegalArgumentException("Execution timeout must not be null or blank");
        }
        try {
            return new ExecutionTimeout(Duration.parse(timeout));
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid ISO-8601 duration format for execution timeout: '" + timeout +
                            "'. Examples: PT30S (30 seconds), PT5M (5 minutes), PT1H (1 hour)",
                    e);
        }
    }

    /**
     * Get timeout in milliseconds for HTTP client configuration.
     * 
     * @return timeout in milliseconds
     */
    public int toMillis() {
        return (int) timeout.toMillis();
    }
}

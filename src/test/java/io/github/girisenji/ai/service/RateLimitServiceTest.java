package io.github.girisenji.ai.service;

import io.github.girisenji.ai.model.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RateLimitService}.
 */
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(100); // 100 requests/hour default
    }

    @Test
    void shouldAllowRequestUnderLimit() {
        // Given
        String toolName = "test_tool";
        String clientIP = "192.168.1.1";

        // When
        boolean allowed = rateLimitService.allowRequest(toolName, clientIP);

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldEnforceRateLimit() {
        // Given
        String toolName = "limited_tool";
        String clientIP = "192.168.1.2";
        RateLimitConfig config = new RateLimitConfig(5, Duration.ofMinutes(1));
        rateLimitService.registerRateLimit(toolName, config);

        // When - Make 5 requests (should all succeed)
        for (int i = 0; i < 5; i++) {
            boolean allowed = rateLimitService.allowRequest(toolName, clientIP);
            assertTrue(allowed, "Request " + (i + 1) + " should be allowed");
        }

        // Then - 6th request should be denied
        boolean denied = rateLimitService.allowRequest(toolName, clientIP);
        assertFalse(denied, "Request 6 should be denied");
    }

    @Test
    void shouldTrackSeparatelyPerClient() {
        // Given
        String toolName = "test_tool";
        String client1 = "192.168.1.1";
        String client2 = "192.168.1.2";
        RateLimitConfig config = new RateLimitConfig(2, Duration.ofMinutes(1));
        rateLimitService.registerRateLimit(toolName, config);

        // When - Client 1 makes 2 requests
        assertTrue(rateLimitService.allowRequest(toolName, client1));
        assertTrue(rateLimitService.allowRequest(toolName, client1));

        // Then - Client 1 should be blocked, but client 2 should still be allowed
        assertFalse(rateLimitService.allowRequest(toolName, client1));
        assertTrue(rateLimitService.allowRequest(toolName, client2));
    }

    @Test
    void shouldTrackSeparatelyPerTool() {
        // Given
        String tool1 = "tool1";
        String tool2 = "tool2";
        String clientIP = "192.168.1.1";
        RateLimitConfig config = new RateLimitConfig(1, Duration.ofMinutes(1));
        rateLimitService.registerRateLimit(tool1, config);
        rateLimitService.registerRateLimit(tool2, config);

        // When
        assertTrue(rateLimitService.allowRequest(tool1, clientIP));
        assertTrue(rateLimitService.allowRequest(tool2, clientIP));

        // Then
        assertFalse(rateLimitService.allowRequest(tool1, clientIP));
        assertFalse(rateLimitService.allowRequest(tool2, clientIP));
    }

    @Test
    void shouldGetCorrectStatus() {
        // Given
        String toolName = "test_tool";
        String clientIP = "192.168.1.1";
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofHours(1));
        rateLimitService.registerRateLimit(toolName, config);

        // When - Make 3 requests
        rateLimitService.allowRequest(toolName, clientIP);
        rateLimitService.allowRequest(toolName, clientIP);
        rateLimitService.allowRequest(toolName, clientIP);

        // Then
        RateLimitService.RateLimitStatus status = rateLimitService.getStatus(toolName, clientIP);
        assertThat(status.toolName()).isEqualTo(toolName);
        assertThat(status.clientIP()).isEqualTo(clientIP);
        assertThat(status.currentRequests()).isEqualTo(3);
        assertThat(status.maxRequests()).isEqualTo(10);
        assertThat(status.remainingRequests()).isEqualTo(7);
        assertThat(status.isLimitExceeded()).isFalse();
    }

    @Test
    void shouldResetRateLimit() {
        // Given
        String toolName = "test_tool";
        String clientIP = "192.168.1.1";
        RateLimitConfig config = new RateLimitConfig(2, Duration.ofMinutes(1));
        rateLimitService.registerRateLimit(toolName, config);

        // When - Exceed limit
        rateLimitService.allowRequest(toolName, clientIP);
        rateLimitService.allowRequest(toolName, clientIP);
        assertFalse(rateLimitService.allowRequest(toolName, clientIP));

        // Reset
        rateLimitService.resetRateLimit(toolName, clientIP);

        // Then - Should be allowed again
        assertTrue(rateLimitService.allowRequest(toolName, clientIP));
    }

    @Test
    void shouldUseDefaultLimitWhenNotConfigured() {
        // Given
        String toolName = "unconfigured_tool";
        String clientIP = "192.168.1.1";

        // When
        RateLimitService.RateLimitStatus status = rateLimitService.getStatus(toolName, clientIP);

        // Then - Should use default limit (100/hour)
        assertThat(status.maxRequests()).isEqualTo(100);
        assertThat(status.window()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void shouldProvideStatistics() {
        // Given
        String toolName = "test_tool";
        String clientIP = "192.168.1.1";
        rateLimitService.allowRequest(toolName, clientIP);

        // When
        var stats = rateLimitService.getStatistics();

        // Then
        assertThat(stats).containsKeys("hitCount", "missCount", "size", "evictionCount");
        assertThat(stats.get("size")).isNotNull();
    }
}

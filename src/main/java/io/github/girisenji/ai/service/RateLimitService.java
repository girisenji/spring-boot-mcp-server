package io.github.girisenji.ai.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.girisenji.ai.model.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for rate limiting tool executions.
 * 
 * <p>
 * Uses an in-memory cache (Caffeine) to track request counts per tool and
 * client.
 * Rate limits are configured per-tool in the approved-tools.yml file.
 * 
 * <p>
 * Key format: "toolName:clientIP:timeWindow"
 * - toolName: The name of the tool being called
 * - clientIP: The IP address of the client making the request
 * - timeWindow: Time bucket (hour, minute, etc.) depending on configuration
 * 
 * <p>
 * Example configuration in approved-tools.yml:
 * 
 * <pre>
 * approvedTools:
 *   - name: get_users
 *     rateLimit:
 *       requests: 100
 *       window: PT1H  # 1 hour (ISO-8601 duration)
 * </pre>
 */
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final Cache<String, AtomicInteger> requestCounts;
    private final Map<String, RateLimitConfig> rateLimitConfigs;
    private final RateLimitConfig defaultRateLimit;

    /**
     * Create a new RateLimitService.
     * 
     * @param defaultRequestsPerHour Default rate limit for tools without specific
     *                               configuration
     */
    public RateLimitService(int defaultRequestsPerHour) {
        this.requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(2)) // Keep counters for 2 hours max
                .maximumSize(100_000) // Limit memory usage
                .recordStats()
                .build();

        this.rateLimitConfigs = new ConcurrentHashMap<>();
        this.defaultRateLimit = new RateLimitConfig(
                defaultRequestsPerHour,
                Duration.ofHours(1));

        log.info("RateLimitService initialized with default limit: {} requests/hour", defaultRequestsPerHour);
    }

    /**
     * Register rate limit configuration for a specific tool.
     * 
     * @param toolName The name of the tool
     * @param config   The rate limit configuration
     */
    public void registerRateLimit(String toolName, RateLimitConfig config) {
        rateLimitConfigs.put(toolName, config);
        log.debug("Registered rate limit for tool '{}': {} requests per {}",
                toolName, config.maxRequests(), config.window());
    }

    /**
     * Check if a request is allowed under the rate limit.
     * 
     * @param toolName The name of the tool being called
     * @param clientIP The IP address of the client
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean allowRequest(String toolName, String clientIP) {
        RateLimitConfig config = getRateLimitConfig(toolName);
        String key = generateKey(toolName, clientIP, config.window());

        AtomicInteger counter = requestCounts.get(key, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();

        boolean allowed = currentCount <= config.maxRequests();

        if (!allowed) {
            log.warn("Rate limit exceeded for tool '{}' from IP '{}': {}/{} requests",
                    toolName, clientIP, currentCount, config.maxRequests());
        } else {
            log.debug("Request allowed for tool '{}' from IP '{}': {}/{} requests",
                    toolName, clientIP, currentCount, config.maxRequests());
        }

        return allowed;
    }

    /**
     * Get current usage for a tool and client.
     * 
     * @param toolName The name of the tool
     * @param clientIP The IP address of the client
     * @return Current request count and limit
     */
    public RateLimitStatus getStatus(String toolName, String clientIP) {
        RateLimitConfig config = getRateLimitConfig(toolName);
        String key = generateKey(toolName, clientIP, config.window());

        AtomicInteger counter = requestCounts.getIfPresent(key);
        int currentCount = (counter != null) ? counter.get() : 0;

        return new RateLimitStatus(
                toolName,
                clientIP,
                currentCount,
                config.maxRequests(),
                config.window());
    }

    /**
     * Reset rate limit for a specific tool and client (admin operation).
     * 
     * @param toolName The name of the tool
     * @param clientIP The IP address of the client
     */
    public void resetRateLimit(String toolName, String clientIP) {
        RateLimitConfig config = getRateLimitConfig(toolName);
        String key = generateKey(toolName, clientIP, config.window());
        requestCounts.invalidate(key);
        log.info("Reset rate limit for tool '{}' from IP '{}'", toolName, clientIP);
    }

    /**
     * Get cache statistics for monitoring.
     */
    public Map<String, Object> getStatistics() {
        var stats = requestCounts.stats();
        return Map.of(
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "loadSuccessCount", stats.loadSuccessCount(),
                "loadFailureCount", stats.loadFailureCount(),
                "evictionCount", stats.evictionCount(),
                "size", requestCounts.estimatedSize());
    }

    /**
     * Generate cache key based on tool name, client IP, and time window.
     */
    private String generateKey(String toolName, String clientIP, Duration window) {
        long timeWindowMillis = window.toMillis();
        long currentBucket = System.currentTimeMillis() / timeWindowMillis;
        return String.format("%s:%s:%d", toolName, clientIP, currentBucket);
    }

    /**
     * Get rate limit configuration for a tool, falling back to default if not
     * configured.
     */
    private RateLimitConfig getRateLimitConfig(String toolName) {
        return rateLimitConfigs.getOrDefault(toolName, defaultRateLimit);
    }

    /**
     * Rate limit status information for a specific tool and client.
     *
     * @param toolName        Name of the tool
     * @param clientIP        IP address of the client
     * @param currentRequests Current number of requests in the time window
     * @param maxRequests     Maximum requests allowed in the time window
     * @param window          Time window for the rate limit
     */
    public record RateLimitStatus(
            String toolName,
            String clientIP,
            int currentRequests,
            int maxRequests,
            Duration window) {

        /**
         * Check if the rate limit has been exceeded.
         *
         * @return true if current requests exceed the maximum allowed
         */
        public boolean isLimitExceeded() {
            return currentRequests > maxRequests;
        }

        /**
         * Calculate remaining requests before hitting the limit.
         *
         * @return number of requests remaining (0 if limit exceeded)
         */
        public int remainingRequests() {
            return Math.max(0, maxRequests - currentRequests);
        }
    }
}

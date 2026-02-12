package io.github.girisenji.ai.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for tracking MCP server metrics using Micrometer.
 * 
 * <p>
 * Provides comprehensive metrics for monitoring tool execution, performance,
 * and system health.
 * Integrates with Spring Boot Actuator and supports Prometheus export.
 * 
 * <p>
 * <b>Metrics Tracked:</b>
 * <ul>
 * <li>Tool execution count (success/failure)</li>
 * <li>Tool execution duration (per-tool latency histograms)</li>
 * <li>Discovery refresh count</li>
 * <li>Active SSE connections (gauge)</li>
 * <li>Rate limit exceeded count</li>
 * </ul>
 * 
 * <p>
 * <b>Usage Example:</b>
 * 
 * <pre>
 * {@code
 * // Track tool execution
 * Timer.Sample sample = metricsService.startTimer();
 * try {
 *     // Execute tool
 *     metricsService.recordToolSuccess(toolName, sample);
 * } catch (Exception e) {
 *     metricsService.recordToolFailure(toolName, sample, e.getClass().getSimpleName());
 * }
 * }
 * </pre>
 */
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    // Metric names (following Prometheus naming conventions)
    private static final String TOOL_EXECUTION_COUNT = "mcp.tool.execution.count";
    private static final String TOOL_EXECUTION_DURATION = "mcp.tool.execution.duration";
    private static final String DISCOVERY_REFRESH_COUNT = "mcp.discovery.refresh.count";
    private static final String SSE_CONNECTIONS_ACTIVE = "mcp.sse.connections.active";
    private static final String RATE_LIMIT_EXCEEDED_COUNT = "mcp.rate_limit.exceeded.count";

    // Tag names
    private static final String TAG_TOOL_NAME = "tool";
    private static final String TAG_STATUS = "status";
    private static final String TAG_ERROR_TYPE = "error";

    // Tag values
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILURE = "failure";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeSseConnections;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.activeSseConnections = new AtomicInteger(0);

        // Register SSE connections gauge
        meterRegistry.gauge(SSE_CONNECTIONS_ACTIVE, activeSseConnections);

        log.info("MetricsService initialized with MeterRegistry: {}", meterRegistry.getClass().getSimpleName());
    }

    /**
     * Start a timer for measuring execution duration.
     * 
     * @return Timer sample to be stopped when execution completes
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Record successful tool execution.
     * 
     * @param toolName Name of the executed tool
     * @param sample   Timer sample started before execution
     */
    public void recordToolSuccess(String toolName, Timer.Sample sample) {
        // Increment success counter
        Counter.builder(TOOL_EXECUTION_COUNT)
                .tag(TAG_TOOL_NAME, toolName)
                .tag(TAG_STATUS, STATUS_SUCCESS)
                .description("Number of tool executions")
                .register(meterRegistry)
                .increment();

        // Record execution duration
        sample.stop(Timer.builder(TOOL_EXECUTION_DURATION)
                .tag(TAG_TOOL_NAME, toolName)
                .tag(TAG_STATUS, STATUS_SUCCESS)
                .description("Tool execution duration")
                .register(meterRegistry));
    }

    /**
     * Record failed tool execution.
     * 
     * @param toolName  Name of the executed tool
     * @param sample    Timer sample started before execution
     * @param errorType Type of error that occurred (e.g., "TimeoutException",
     *                  "RateLimitExceeded")
     */
    public void recordToolFailure(String toolName, Timer.Sample sample, String errorType) {
        // Increment failure counter
        Counter.builder(TOOL_EXECUTION_COUNT)
                .tag(TAG_TOOL_NAME, toolName)
                .tag(TAG_STATUS, STATUS_FAILURE)
                .tag(TAG_ERROR_TYPE, errorType)
                .description("Number of tool executions")
                .register(meterRegistry)
                .increment();

        // Record execution duration
        sample.stop(Timer.builder(TOOL_EXECUTION_DURATION)
                .tag(TAG_TOOL_NAME, toolName)
                .tag(TAG_STATUS, STATUS_FAILURE)
                .tag(TAG_ERROR_TYPE, errorType)
                .description("Tool execution duration")
                .register(meterRegistry));
    }

    /**
     * Record tool execution duration without starting a timer.
     * Useful when you already have the duration.
     * 
     * @param toolName Name of the executed tool
     * @param duration Execution duration
     * @param success  Whether execution was successful
     */
    public void recordToolExecution(String toolName, Duration duration, boolean success) {
        String status = success ? STATUS_SUCCESS : STATUS_FAILURE;

        // Increment counter
        Counter.builder(TOOL_EXECUTION_COUNT)
                .tag(TAG_TOOL_NAME, toolName)
                .tag(TAG_STATUS, status)
                .description("Number of tool executions")
                .register(meterRegistry)
                .increment();

        // Record duration
        Timer.builder(TOOL_EXECUTION_DURATION)
                .tag(TAG_TOOL_NAME, toolName)
                .tag(TAG_STATUS, status)
                .description("Tool execution duration")
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * Record discovery refresh event.
     */
    public void recordDiscoveryRefresh() {
        Counter.builder(DISCOVERY_REFRESH_COUNT)
                .description("Number of times tool discovery was refreshed")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record rate limit exceeded event.
     * 
     * @param toolName Name of the tool that hit rate limit
     */
    public void recordRateLimitExceeded(String toolName) {
        Counter.builder(RATE_LIMIT_EXCEEDED_COUNT)
                .tag(TAG_TOOL_NAME, toolName)
                .description("Number of times rate limit was exceeded")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increment active SSE connection count.
     */
    public void incrementSseConnections() {
        int count = activeSseConnections.incrementAndGet();
        log.debug("SSE connection opened. Active connections: {}", count);
    }

    /**
     * Decrement active SSE connection count.
     * Will not decrement below zero.
     */
    public void decrementSseConnections() {
        // Use compareAndSet to prevent going below zero
        int currentValue;
        int newValue;
        do {
            currentValue = activeSseConnections.get();
            newValue = Math.max(0, currentValue - 1);
        } while (!activeSseConnections.compareAndSet(currentValue, newValue));

        log.debug("SSE connection closed. Active connections: {} -> {}", currentValue, newValue);
    }

    /**
     * Get current count of active SSE connections.
     * 
     * @return Number of active SSE connections
     */
    public int getActiveSseConnectionCount() {
        return activeSseConnections.get();
    }

    /**
     * Get the underlying MeterRegistry for advanced usage.
     * 
     * @return Micrometer MeterRegistry
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}

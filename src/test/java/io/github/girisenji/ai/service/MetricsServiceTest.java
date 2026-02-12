package io.github.girisenji.ai.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the MetricsService class.
 * Tests metric recording, timing, and gauge tracking functionality.
 */
class MetricsServiceTest {

    private MeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void startTimer_shouldReturnTimerSample() {
        // When
        Timer.Sample sample = metricsService.startTimer();

        // Then
        assertNotNull(sample, "Timer sample should not be null");
    }

    @Test
    void recordToolSuccess_shouldIncrementSuccessCounter() {
        // Given
        String toolName = "testTool";
        Timer.Sample sample = metricsService.startTimer();

        // Simulate small delay
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        metricsService.recordToolSuccess(toolName, sample);

        // Then
        Counter counter = meterRegistry.find("mcp.tool.execution.count")
                .tag("tool", toolName)
                .tag("status", "success")
                .counter();
        assertNotNull(counter, "Success counter should exist");
        assertEquals(1.0, counter.count(), "Success count should be 1");

        Timer timer = meterRegistry.find("mcp.tool.execution.duration")
                .tag("tool", toolName)
                .timer();
        assertNotNull(timer, "Duration timer should exist");
        assertEquals(1, timer.count(), "Timer count should be 1");
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 10,
                "Duration should be at least 10ms");
    }

    @Test
    void recordToolFailure_shouldIncrementFailureCounter() {
        // Given
        String toolName = "failingTool";
        String errorType = "Timeout";
        Timer.Sample sample = metricsService.startTimer();

        // When
        metricsService.recordToolFailure(toolName, sample, errorType);

        // Then
        Counter counter = meterRegistry.find("mcp.tool.execution.count")
                .tag("tool", toolName)
                .tag("status", "failure")
                .tag("error", errorType)
                .counter();
        assertNotNull(counter, "Failure counter should exist");
        assertEquals(1.0, counter.count(), "Failure count should be 1");

        Timer timer = meterRegistry.find("mcp.tool.execution.duration")
                .tag("tool", toolName)
                .timer();
        assertNotNull(timer, "Duration timer should exist");
        assertEquals(1, timer.count(), "Timer count should be 1");
    }

    @Test
    void recordToolFailure_differentErrorTypes_shouldCreateSeparateCounters() {
        // Given
        String toolName = "multiFailTool";
        Timer.Sample sample1 = metricsService.startTimer();
        Timer.Sample sample2 = metricsService.startTimer();
        Timer.Sample sample3 = metricsService.startTimer();

        // When
        metricsService.recordToolFailure(toolName, sample1, "Timeout");
        metricsService.recordToolFailure(toolName, sample2, "RateLimitExceeded");
        metricsService.recordToolFailure(toolName, sample3, "Timeout");

        // Then
        Counter timeoutCounter = meterRegistry.find("mcp.tool.execution.count")
                .tag("tool", toolName)
                .tag("status", "failure")
                .tag("error", "Timeout")
                .counter();
        assertEquals(2.0, timeoutCounter.count(), "Timeout failures should be 2");

        Counter rateLimitCounter = meterRegistry.find("mcp.tool.execution.count")
                .tag("tool", toolName)
                .tag("status", "failure")
                .tag("error", "RateLimitExceeded")
                .counter();
        assertEquals(1.0, rateLimitCounter.count(), "Rate limit failures should be 1");
    }

    @Test
    void recordDiscoveryRefresh_shouldIncrementCounter() {
        // When
        metricsService.recordDiscoveryRefresh();
        metricsService.recordDiscoveryRefresh();

        // Then
        Counter counter = meterRegistry.find("mcp.discovery.refresh.count").counter();
        assertNotNull(counter, "Discovery refresh counter should exist");
        assertEquals(2.0, counter.count(), "Refresh count should be 2");
    }

    @Test
    void sseConnectionTracking_shouldIncrementAndDecrement() {
        // When - Initial increment
        metricsService.incrementSseConnections();

        // Then - Check via actual gauge value
        var gauge1 = meterRegistry.find("mcp.sse.connections.active").gauge();
        assertNotNull(gauge1, "SSE connections gauge should exist");
        assertEquals(1.0, gauge1.value(), "SSE connections should be 1");

        // When - Second increment
        metricsService.incrementSseConnections();

        // Then
        var gauge2 = meterRegistry.find("mcp.sse.connections.active").gauge();
        assertEquals(2.0, gauge2.value(), "SSE connections should be 2");

        // When - Decrement
        metricsService.decrementSseConnections();

        // Then
        var gauge3 = meterRegistry.find("mcp.sse.connections.active").gauge();
        assertEquals(1.0, gauge3.value(), "SSE connections should be 1 after decrement");

        // When - Decrement to zero
        metricsService.decrementSseConnections();

        // Then
        var gauge4 = meterRegistry.find("mcp.sse.connections.active").gauge();
        assertEquals(0.0, gauge4.value(), "SSE connections should be 0");
        String toolName = "rateLimitedTool";

        // When
        metricsService.recordRateLimitExceeded(toolName);
        metricsService.recordRateLimitExceeded(toolName);
        metricsService.recordRateLimitExceeded(toolName);

        // Then
        Counter counter = meterRegistry.find("mcp.rate_limit.exceeded.count")
                .tag("tool", toolName)
                .counter();
        assertNotNull(counter, "Rate limit exceeded counter should exist");
        assertEquals(3.0, counter.count(), "Rate limit exceeded count should be 3");
    }

    @Test
    void multipleTools_shouldTrackIndependently() {
        // Given
        String tool1 = "tool1";
        String tool2 = "tool2";
        Timer.Sample sample1 = metricsService.startTimer();
        Timer.Sample sample2 = metricsService.startTimer();
        Timer.Sample sample3 = metricsService.startTimer();

        // When
        metricsService.recordToolSuccess(tool1, sample1);
        metricsService.recordToolSuccess(tool1, sample2);
        metricsService.recordToolSuccess(tool2, sample3);

        // Then
        Counter counter1 = meterRegistry.find("mcp.tool.execution.count")
                .tag("tool", tool1)
                .tag("status", "success")
                .counter();
        assertEquals(2.0, counter1.count(), "Tool1 success count should be 2");

        Counter counter2 = meterRegistry.find("mcp.tool.execution.count")
                .tag("tool", tool2)
                .tag("status", "success")
                .counter();
        assertEquals(1.0, counter2.count(), "Tool2 success count should be 1");
    }

    @Test
    void mixedSuccessAndFailure_shouldTrackSeparately() {
        // Given
        String toolName = "mixedTool";
        Timer.Sample sample1 = metricsService.startTimer();
        Timer.Sample sample2 = metricsService.startTimer();
        Timer.Sample sample3 = metricsService.startTimer();

        // When
        metricsService.recordToolSuccess(toolName, sample1);
        metricsService.recordToolFailure(toolName, sample2, "HttpError");
        metricsService.recordToolSuccess(toolName, sample3);

        // Then
        Counter successCounter = meterRegistry.find("mcp.tool.execution.count")
                .tag("tool", toolName)
                .tag("status", "success")
                .counter();
        assertEquals(2.0, successCounter.count(), "Success count should be 2");

        Counter failureCounter = meterRegistry.find("mcp.tool.execution.count")
                .tag("tool", toolName)
                .tag("status", "failure")
                .counter();
        assertEquals(1.0, failureCounter.count(), "Failure count should be 1");
    }

    @Test
    void sseConnections_shouldNotGoBelowZero() {
        // When - Try to decrement from zero
        metricsService.decrementSseConnections();

        // Then
        var gauge = meterRegistry.find("mcp.sse.connections.active").gauge();
        assertNotNull(gauge, "SSE connections gauge should exist");
        assertEquals(0.0, gauge.value(), "SSE connections should not go below 0");
    }

    @Test
    void allMetrics_shouldUseMcpPrefix() {
        // Given
        String toolName = "prefixTest";
        Timer.Sample sample = metricsService.startTimer();

        // When - Record various metrics
        metricsService.recordToolSuccess(toolName, sample);
        metricsService.recordRateLimitExceeded(toolName);
        metricsService.recordDiscoveryRefresh();
        metricsService.incrementSseConnections();

        // Then - All metrics should start with "mcp."
        assertTrue(meterRegistry.find("mcp.tool.execution.count").counter() != null,
                "Tool executions metric should have mcp prefix");
        assertTrue(meterRegistry.find("mcp.tool.execution.duration").timer() != null,
                "Tool duration metric should have mcp prefix");
        assertTrue(meterRegistry.find("mcp.rate_limit.exceeded.count").counter() != null,
                "Rate limit metric should have mcp prefix");
        assertTrue(meterRegistry.find("mcp.discovery.refresh.count").counter() != null,
                "Discovery refresh metric should have mcp prefix");
        assertTrue(meterRegistry.find("mcp.sse.connections.active").gauge() != null,
                "SSE connections metric should have mcp prefix");
    }
}

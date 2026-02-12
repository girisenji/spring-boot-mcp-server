package io.github.girisenji.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.girisenji.ai.model.RateLimitConfig;
import io.github.girisenji.ai.model.ToolExecutionMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link McpToolExecutor} with rate limiting.
 */
class McpToolExecutorIntegrationTest {

    private ObjectMapper objectMapper;
    private ApplicationContext applicationContext;
    private RateLimitService rateLimitService;
    private McpToolExecutor executor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        applicationContext = mock(ApplicationContext.class);
        rateLimitService = new RateLimitService(100);

        // Clear any request context from previous tests
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldEnforceRateLimitWhenEnabled() {
        // Given - Executor with rate limiting enabled
        executor = new McpToolExecutor(
                applicationContext,
                objectMapper,
                "http://localhost:8080",
                rateLimitService,
                true); // rate limiting enabled

        // Register a rate limit for test tool
        RateLimitConfig config = new RateLimitConfig(2, Duration.ofMinutes(1));
        rateLimitService.registerRateLimit("testTool", config);

        // Register metadata for the tool
        ToolExecutionMetadata metadata = new ToolExecutionMetadata(
                "/api/test",
                HttpMethod.GET,
                Map.of(),
                "application/json",
                "REST");
        executor.registerToolMetadata("testTool", metadata);

        // When/Then - First 2 requests should succeed (but will fail due to no HTTP
        // server, that's OK)
        // We're testing rate limiting logic, not HTTP execution
        var result1 = executor.executeTool("testTool", Map.of());
        var result2 = executor.executeTool("testTool", Map.of());
        var result3 = executor.executeTool("testTool", Map.of());

        // Third request should be rate limited
        assertThat(result3.isError()).isTrue();
        assertThat(result3.content()).hasSize(1);
        assertThat(result3.content().get(0).text()).contains("Rate limit exceeded");
    }

    @Test
    void shouldNotEnforceRateLimitWhenDisabled() {
        // Given - Executor with rate limiting disabled
        executor = new McpToolExecutor(
                applicationContext,
                objectMapper,
                "http://localhost:8080",
                rateLimitService,
                false); // rate limiting disabled

        // Register a strict rate limit
        RateLimitConfig config = new RateLimitConfig(1, Duration.ofMinutes(1));
        rateLimitService.registerRateLimit("testTool", config);

        // Register metadata for the tool
        ToolExecutionMetadata metadata = new ToolExecutionMetadata(
                "/api/test",
                HttpMethod.GET,
                Map.of(),
                "application/json",
                "REST");
        executor.registerToolMetadata("testTool", metadata);

        // When - Make multiple requests (more than the limit)
        var result1 = executor.executeTool("testTool", Map.of());
        var result2 = executor.executeTool("testTool", Map.of());
        var result3 = executor.executeTool("testTool", Map.of());

        // Then - Should not be rate limited (will fail on HTTP, but not on rate
        // limiting)
        // All should have error messages about HTTP execution, not rate limiting
        assertThat(result1.content().get(0).text()).doesNotContain("Rate limit exceeded");
        assertThat(result2.content().get(0).text()).doesNotContain("Rate limit exceeded");
        assertThat(result3.content().get(0).text()).doesNotContain("Rate limit exceeded");
    }

    @Test
    void shouldProvideHelpfulErrorMessageOnRateLimit() {
        // Given
        executor = new McpToolExecutor(
                applicationContext,
                objectMapper,
                "http://localhost:8080",
                rateLimitService,
                true);

        RateLimitConfig config = new RateLimitConfig(5, Duration.ofHours(1));
        rateLimitService.registerRateLimit("testTool", config);

        ToolExecutionMetadata metadata = new ToolExecutionMetadata(
                "/api/test",
                HttpMethod.GET,
                Map.of(),
                "application/json",
                "REST");
        executor.registerToolMetadata("testTool", metadata);

        // When - Exhaust the limit
        for (int i = 0; i < 5; i++) {
            executor.executeTool("testTool", Map.of());
        }
        var result = executor.executeTool("testTool", Map.of());

        // Then - Error message should be informative
        String errorMessage = result.content().get(0).text();
        assertThat(errorMessage).contains("Rate limit exceeded");
        assertThat(errorMessage).contains("testTool");
        assertThat(errorMessage).contains("5 requests");
        assertThat(errorMessage).contains("1 hour");
    }

    @Test
    void shouldUseDefaultRateLimitForUnconfiguredTools() {
        // Given - Tool without specific rate limit configuration
        executor = new McpToolExecutor(
                applicationContext,
                objectMapper,
                "http://localhost:8080",
                new RateLimitService(2), // default: 2 requests/hour
                true);

        ToolExecutionMetadata metadata = new ToolExecutionMetadata(
                "/api/test",
                HttpMethod.GET,
                Map.of(),
                "application/json",
                "REST");
        executor.registerToolMetadata("unconfiguredTool", metadata);

        // When - Make requests up to default limit
        var result1 = executor.executeTool("unconfiguredTool", Map.of());
        var result2 = executor.executeTool("unconfiguredTool", Map.of());
        var result3 = executor.executeTool("unconfiguredTool", Map.of());

        // Then - Should be rate limited based on default
        assertThat(result3.content().get(0).text()).contains("Rate limit exceeded");
    }

    @Test
    void shouldHandleMissingToolMetadata() {
        // Given
        executor = new McpToolExecutor(
                applicationContext,
                objectMapper,
                "http://localhost:8080",
                rateLimitService,
                true);

        // When - Execute tool without registering metadata
        var result = executor.executeTool("unknownTool", Map.of());

        // Then - Should return helpful error
        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).text()).contains("execution metadata not available");
    }
}

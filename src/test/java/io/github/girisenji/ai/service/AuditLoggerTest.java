package io.github.girisenji.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.girisenji.ai.model.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuditLogger}.
 */
class AuditLoggerTest {

    private ObjectMapper objectMapper;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger auditLoggerLog;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // Support for Java 8 date/time types

        // Set up logback test appender to capture log output
        auditLoggerLog = (Logger) LoggerFactory.getLogger(AuditLogger.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        auditLoggerLog.addAppender(logAppender);
    }

    // ========== Basic Logging Tests ==========

    @Test
    void shouldNotLogWhenDisabled() {
        // Given
        AuditLogger auditLogger = new AuditLogger(false, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> metadata = Map.of("key", "value");
        AuditEvent event = AuditEvent.toolExecution("testTool", "192.168.1.1", true, null, metadata);

        // When
        auditLogger.log(event);

        // Then
        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void shouldLogWhenEnabled() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> metadata = Map.of("key", "value");
        AuditEvent event = AuditEvent.toolExecution("testTool", "192.168.1.1", true, null, metadata);

        // When
        auditLogger.log(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getFormattedMessage()).contains("[AUDIT]");
        assertThat(logEvent.getFormattedMessage()).contains("TOOL_EXECUTION");
        assertThat(logEvent.getFormattedMessage()).contains("testTool");
        assertThat(logEvent.getFormattedMessage()).contains("192.168.1.1");
    }

    // ========== Format Tests ==========

    @Test
    void shouldLogInPlainFormat() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> metadata = Map.of("durationMs", 123);
        AuditEvent event = AuditEvent.toolExecution("testTool", "192.168.1.1", true, null, metadata);

        // When
        auditLogger.log(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("[AUDIT]");
        assertThat(message).contains("TOOL_EXECUTION");
        assertThat(message).contains("tool=testTool");
        assertThat(message).contains("ip=192.168.1.1");
        assertThat(message).contains("success=true");
        assertThat(message).contains("metadata={durationMs=123}");
    }

    @Test
    void shouldLogInJsonFormat() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.JSON, objectMapper);
        Map<String, Object> metadata = Map.of("durationMs", 123);
        AuditEvent event = AuditEvent.toolExecution("testTool", "192.168.1.1", true, null, metadata);

        // When
        auditLogger.log(event);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("[AUDIT]");
        assertThat(message).contains("\"eventType\":\"TOOL_EXECUTION\"");
        assertThat(message).contains("\"toolName\":\"testTool\"");
        assertThat(message).contains("\"clientIP\":\"192.168.1.1\"");
        assertThat(message).contains("\"success\":true");
    }

    // ========== Tool Execution Logging Tests ==========

    @Test
    void shouldLogSuccessfulToolExecution() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> arguments = Map.of("param1", "value1", "param2", 123);

        // When
        auditLogger.logToolExecution("get_users", arguments, "10.0.0.1", true, null, 250);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("TOOL_EXECUTION");
        assertThat(message).contains("tool=get_users");
        assertThat(message).contains("ip=10.0.0.1");
        assertThat(message).contains("success=true");
        assertThat(message).contains("durationMs=250");
    }

    @Test
    void shouldLogFailedToolExecution() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> arguments = Map.of("userId", "123");

        // When
        auditLogger.logToolExecution("delete_user", arguments, "10.0.0.2", false, "User not found", 50);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        String message = logEvent.getFormattedMessage();
        assertThat(message).contains("TOOL_EXECUTION");
        assertThat(message).contains("tool=delete_user");
        assertThat(message).contains("ip=10.0.0.2");
        assertThat(message).contains("success=false");
        assertThat(message).contains("error=User not found");
        assertThat(logEvent.getLevel().toString()).isEqualTo("WARN"); // Failed executions should be WARN
    }

    // ========== Argument Sanitization Tests ==========

    @Test
    void shouldSanitizePasswordFields() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> arguments = Map.of(
                "username", "john",
                "password", "secret123",
                "data", "normalValue");

        // When
        auditLogger.logToolExecution("login", arguments, "10.0.0.1", true, null, 100);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("username");
        assertThat(message).contains("[REDACTED]");
        assertThat(message).doesNotContain("secret123");
        assertThat(message).contains("normalValue");
    }

    @Test
    void shouldSanitizeTokenFields() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> arguments = Map.of(
                "apiKey", "key_12345",
                "token", "bearer_xyz",
                "accessToken", "access_abc");

        // When
        auditLogger.logToolExecution("api_call", arguments, "10.0.0.1", true, null, 100);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("[REDACTED]");
        assertThat(message).doesNotContain("key_12345");
        assertThat(message).doesNotContain("bearer_xyz");
        assertThat(message).doesNotContain("access_abc");
    }

    @Test
    void shouldSanitizeNestedSensitiveFields() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> nested = Map.of(
                "username", "alice",
                "password", "hidden",
                "role", "admin");
        Map<String, Object> arguments = Map.of(
                "user", nested,
                "action", "create");

        // When
        auditLogger.logToolExecution("create_user", arguments, "10.0.0.1", true, null, 150);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("[REDACTED]");
        assertThat(message).doesNotContain("hidden");
        assertThat(message).contains("admin");
    }

    @Test
    void shouldHandleNullAndEmptyArguments() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);

        // When - null arguments
        auditLogger.logToolExecution("null_args", null, "10.0.0.1", true, null, 10);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message1 = logAppender.list.get(0).getFormattedMessage();
        assertThat(message1).contains("TOOL_EXECUTION");

        // Clear log
        logAppender.list.clear();

        // When - empty arguments
        auditLogger.logToolExecution("empty_args", Map.of(), "10.0.0.1", true, null, 10);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message2 = logAppender.list.get(0).getFormattedMessage();
        assertThat(message2).contains("TOOL_EXECUTION");
    }

    // ========== Security Event Logging Tests ==========

    @Test
    void shouldLogRateLimitExceeded() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);

        // When
        auditLogger.logRateLimitExceeded("expensive_operation", "10.0.0.1", 105, 100);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        String message = logEvent.getFormattedMessage();
        assertThat(message).contains("RATE_LIMIT_EXCEEDED");
        assertThat(message).contains("tool=expensive_operation");
        assertThat(message).contains("ip=10.0.0.1");
        assertThat(message).contains("success=false");
        assertThat(message).contains("requestsInWindow=105");
        assertThat(message).contains("maxRequests=100");
        assertThat(logEvent.getLevel().toString()).isEqualTo("WARN");
    }

    @Test
    void shouldLogExecutionTimeout() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);

        // When
        auditLogger.logExecutionTimeout("slow_operation", "10.0.0.2", "Request timeout after 30s", 30000);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        String message = logEvent.getFormattedMessage();
        assertThat(message).contains("EXECUTION_TIMEOUT");
        assertThat(message).contains("tool=slow_operation");
        assertThat(message).contains("ip=10.0.0.2");
        assertThat(message).contains("success=false");
        assertThat(message).contains("error=Request timeout after 30s");
        assertThat(message).contains("timeoutMs=30000");
        assertThat(logEvent.getLevel().toString()).isEqualTo("WARN");
    }

    @Test
    void shouldLogSizeLimitExceeded() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);

        // When
        auditLogger.logSizeLimitExceeded(
                "upload_file",
                "10.0.0.3",
                "Request size exceeds 10MB limit",
                15_000_000,
                10_000_000);

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        String message = logEvent.getFormattedMessage();
        assertThat(message).contains("SIZE_LIMIT_EXCEEDED");
        assertThat(message).contains("tool=upload_file");
        assertThat(message).contains("ip=10.0.0.3");
        assertThat(message).contains("success=false");
        assertThat(message).contains("actualSizeBytes=15000000");
        assertThat(message).contains("maxSizeBytes=10000000");
        assertThat(logEvent.getLevel().toString()).isEqualTo("WARN");
    }

    // ========== Approval Change Logging Tests ==========

    @Test
    void shouldLogApprovalChange() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);

        // When
        auditLogger.logApprovalChange("new_tool", "172.16.0.1", true, "Tool approved via admin UI");

        // Then
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        String message = logEvent.getFormattedMessage();
        assertThat(message).contains("APPROVAL_CHANGE");
        assertThat(message).contains("tool=new_tool");
        assertThat(message).contains("ip=172.16.0.1");
        assertThat(message).contains("success=true");
        assertThat(message).contains("approved=true");
        assertThat(message).contains("action=Tool approved via admin UI");
        assertThat(logEvent.getLevel().toString()).isEqualTo("INFO");
    }

    @Test
    void shouldLogToolRejection() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);

        // When
        auditLogger.logApprovalChange("risky_tool", "172.16.0.1", false, "Tool removed from approved list");

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("APPROVAL_CHANGE");
        assertThat(message).contains("tool=risky_tool");
        assertThat(message).contains("approved=false");
        assertThat(message).contains("action=Tool removed from approved list");
    }

    // ========== Edge Cases ==========

    @Test
    void shouldHandleCaseInsensitiveSensitiveFields() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);
        Map<String, Object> arguments = Map.of(
                "PASSWORD", "secret1",
                "Password", "secret2",
                "ApiKey", "key123");

        // When
        auditLogger.logToolExecution("test", arguments, "10.0.0.1", true, null, 10);

        // Then
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("[REDACTED]");
        assertThat(message).doesNotContain("secret1");
        assertThat(message).doesNotContain("secret2");
        assertThat(message).doesNotContain("key123");
    }

    @Test
    void shouldLogSuccessAsInfoAndFailureAsWarn() {
        // Given
        AuditLogger auditLogger = new AuditLogger(true, AuditLogger.LogFormat.PLAIN, objectMapper);

        // When - success
        Map<String, Object> metadata = Map.of("key", "value");
        AuditEvent successEvent = AuditEvent.toolExecution("tool1", "10.0.0.1", true, null, metadata);
        auditLogger.log(successEvent);

        // Then
        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getLevel().toString()).isEqualTo("INFO");

        // Clear
        logAppender.list.clear();

        // When - failure
        AuditEvent failureEvent = AuditEvent.toolExecution("tool2", "10.0.0.1", false, "Error", metadata);
        auditLogger.log(failureEvent);

        // Then
        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getLevel().toString()).isEqualTo("WARN");
    }
}

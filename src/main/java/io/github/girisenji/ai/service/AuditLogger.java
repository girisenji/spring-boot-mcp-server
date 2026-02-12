package io.github.girisenji.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.girisenji.ai.model.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for logging audit events in the MCP server.
 * 
 * <p>
 * Provides structured logging of security-relevant events such as:
 * <ul>
 * <li>Tool executions (name, arguments, client IP, result)</li>
 * <li>Tool approval changes (admin actions)</li>
 * <li>Rate limit violations</li>
 * <li>Timeout events</li>
 * <li>Size limit violations</li>
 * </ul>
 * 
 * <p>
 * Supports both plain text and JSON log formats for integration
 * with SIEM systems and log aggregators.
 */
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final boolean enabled;
    private final LogFormat format;
    private final ObjectMapper objectMapper;
    private final Set<String> sensitiveFields;
    private final Pattern sensitivePattern;

    /**
     * Log format for audit events.
     */
    public enum LogFormat {
        /**
         * Human-readable plain text format.
         */
        PLAIN,

        /**
         * Structured JSON format for SIEM integration.
         */
        JSON
    }

    public AuditLogger(
            boolean enabled,
            LogFormat format,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.format = format;
        this.objectMapper = objectMapper;

        // Common sensitive field names to sanitize (all lowercase for case-insensitive
        // matching)
        this.sensitiveFields = Set.of(
                "password", "passwd", "pwd",
                "secret", "apikey", "api_key",
                "token", "accesstoken", "access_token",
                "authorization", "auth",
                "credential", "credentials");

        // Pattern for sensitive values (anything that looks like a token/key)
        this.sensitivePattern = Pattern.compile(
                "(?i)(password|secret|token|key|auth).*",
                Pattern.CASE_INSENSITIVE);
    }

    /**
     * Log an audit event if audit logging is enabled.
     */
    public void log(AuditEvent event) {
        if (!enabled) {
            return;
        }

        if (format == LogFormat.JSON) {
            logJson(event);
        } else {
            logPlain(event);
        }
    }

    /**
     * Log a tool execution event with sanitized arguments.
     */
    public void logToolExecution(
            String toolName,
            Map<String, Object> arguments,
            String clientIP,
            boolean success,
            String errorMessage,
            long durationMs) {

        if (!enabled) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("arguments", sanitizeArguments(arguments));
        metadata.put("durationMs", durationMs);

        AuditEvent event = AuditEvent.toolExecution(
                toolName,
                clientIP,
                success,
                errorMessage,
                metadata);

        log(event);
    }

    /**
     * Log a tool approval change event.
     */
    public void logApprovalChange(
            String toolName,
            String clientIP,
            boolean approved,
            String action) {

        if (!enabled) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("approved", approved);
        metadata.put("action", action);

        AuditEvent event = AuditEvent.approvalChange(
                toolName,
                clientIP,
                approved,
                metadata);

        log(event);
    }

    /**
     * Log a rate limit exceeded event.
     */
    public void logRateLimitExceeded(
            String toolName,
            String clientIP,
            int requestsInWindow,
            int maxRequests) {

        if (!enabled) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("requestsInWindow", requestsInWindow);
        metadata.put("maxRequests", maxRequests);

        AuditEvent event = AuditEvent.rateLimitExceeded(
                toolName,
                clientIP,
                metadata);

        log(event);
    }

    /**
     * Log an execution timeout event.
     */
    public void logExecutionTimeout(
            String toolName,
            String clientIP,
            String errorMessage,
            long timeoutMs) {

        if (!enabled) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timeoutMs", timeoutMs);

        AuditEvent event = AuditEvent.executionTimeout(
                toolName,
                clientIP,
                errorMessage,
                metadata);

        log(event);
    }

    /**
     * Log a size limit exceeded event.
     */
    public void logSizeLimitExceeded(
            String toolName,
            String clientIP,
            String errorMessage,
            long actualSize,
            long maxSize) {

        if (!enabled) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("actualSizeBytes", actualSize);
        metadata.put("maxSizeBytes", maxSize);

        AuditEvent event = AuditEvent.sizeLimitExceeded(
                toolName,
                clientIP,
                errorMessage,
                metadata);

        log(event);
    }

    /**
     * Sanitize arguments by replacing sensitive values with "[REDACTED]".
     */
    private Map<String, Object> sanitizeArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new HashMap<>();
        arguments.forEach((key, value) -> {
            if (isSensitiveField(key)) {
                sanitized.put(key, "[REDACTED]");
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                sanitized.put(key, sanitizeArguments(nestedMap));
            } else {
                sanitized.put(key, value);
            }
        });

        return sanitized;
    }

    /**
     * Check if a field name indicates sensitive data.
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        String lowerFieldName = fieldName.toLowerCase();
        return sensitiveFields.contains(lowerFieldName) ||
                sensitivePattern.matcher(fieldName).matches();
    }

    /**
     * Log event in plain text format.
     */
    private void logPlain(AuditEvent event) {
        StringBuilder message = new StringBuilder();
        message.append("[AUDIT] ");
        message.append(event.eventType());
        message.append(" | timestamp=").append(event.timestamp());

        if (event.toolName() != null) {
            message.append(" | tool=").append(event.toolName());
        }

        if (event.clientIP() != null) {
            message.append(" | ip=").append(event.clientIP());
        }

        message.append(" | success=").append(event.success());

        if (event.errorMessage() != null) {
            message.append(" | error=").append(event.errorMessage());
        }

        if (event.metadata() != null && !event.metadata().isEmpty()) {
            message.append(" | metadata=").append(event.metadata());
        }

        if (event.success()) {
            log.info(message.toString());
        } else {
            log.warn(message.toString());
        }
    }

    /**
     * Log event in JSON format.
     */
    private void logJson(AuditEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            if (event.success()) {
                log.info("[AUDIT] {}", json);
            } else {
                log.warn("[AUDIT] {}", json);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit event to JSON", e);
            // Fall back to plain format
            logPlain(event);
        }
    }
}

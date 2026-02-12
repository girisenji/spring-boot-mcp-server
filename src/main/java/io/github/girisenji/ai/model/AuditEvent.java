package io.github.girisenji.ai.model;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an auditable event in the MCP server.
 * 
 * <p>
 * Audit events track security-relevant operations such as tool executions
 * and configuration changes for compliance and debugging purposes.
 * 
 * @param eventType    Type of event (TOOL_EXECUTION, APPROVAL_CHANGE, etc.)
 * @param timestamp    When the event occurred (ISO-8601 format)
 * @param toolName     Name of the tool involved (null for non-tool events)
 * @param clientIP     IP address of the client that triggered the event
 * @param username     Username if authenticated (null for anonymous)
 * @param success      Whether the operation succeeded
 * @param errorMessage Error message if operation failed (null if successful)
 * @param metadata     Additional event-specific metadata
 */
public record AuditEvent(
        EventType eventType,
        Instant timestamp,
        String toolName,
        String clientIP,
        String username,
        boolean success,
        String errorMessage,
        Map<String, Object> metadata) {

    /**
     * Type of auditable event.
     */
    public enum EventType {
        /**
         * Tool execution event (MCP tool call).
         */
        TOOL_EXECUTION,

        /**
         * Tool approval change event (admin action).
         */
        APPROVAL_CHANGE,

        /**
         * Rate limit exceeded event (security).
         */
        RATE_LIMIT_EXCEEDED,

        /**
         * Execution timeout event (performance).
         */
        EXECUTION_TIMEOUT,

        /**
         * Request size limit exceeded event (security).
         */
        SIZE_LIMIT_EXCEEDED
    }

    /**
     * Create a tool execution audit event.
     */
    public static AuditEvent toolExecution(
            String toolName,
            String clientIP,
            boolean success,
            String errorMessage,
            Map<String, Object> metadata) {
        return new AuditEvent(
                EventType.TOOL_EXECUTION,
                Instant.now(),
                toolName,
                clientIP,
                null, // username - can be added later if authentication is implemented
                success,
                errorMessage,
                metadata);
    }

    /**
     * Create an approval change audit event.
     */
    public static AuditEvent approvalChange(
            String toolName,
            String clientIP,
            boolean approved,
            Map<String, Object> metadata) {
        return new AuditEvent(
                EventType.APPROVAL_CHANGE,
                Instant.now(),
                toolName,
                clientIP,
                null,
                true, // approval changes are always successful
                null,
                metadata);
    }

    /**
     * Create a rate limit exceeded audit event.
     */
    public static AuditEvent rateLimitExceeded(
            String toolName,
            String clientIP,
            Map<String, Object> metadata) {
        return new AuditEvent(
                EventType.RATE_LIMIT_EXCEEDED,
                Instant.now(),
                toolName,
                clientIP,
                null,
                false,
                "Rate limit exceeded",
                metadata);
    }

    /**
     * Create an execution timeout audit event.
     */
    public static AuditEvent executionTimeout(
            String toolName,
            String clientIP,
            String errorMessage,
            Map<String, Object> metadata) {
        return new AuditEvent(
                EventType.EXECUTION_TIMEOUT,
                Instant.now(),
                toolName,
                clientIP,
                null,
                false,
                errorMessage,
                metadata);
    }

    /**
     * Create a size limit exceeded audit event.
     */
    public static AuditEvent sizeLimitExceeded(
            String toolName,
            String clientIP,
            String errorMessage,
            Map<String, Object> metadata) {
        return new AuditEvent(
                EventType.SIZE_LIMIT_EXCEEDED,
                Instant.now(),
                toolName,
                clientIP,
                null,
                false,
                errorMessage,
                metadata);
    }
}

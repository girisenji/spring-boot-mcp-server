package io.github.girisenji.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.girisenji.ai.config.AutoMcpServerProperties;
import io.github.girisenji.ai.mcp.McpProtocol;
import io.github.girisenji.ai.model.ExecutionTimeout;
import io.github.girisenji.ai.model.SizeLimit;
import io.github.girisenji.ai.model.ToolExecutionMetadata;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for executing MCP tool calls via HTTP requests.
 * Executes calls to discovered REST/GraphQL endpoints.
 * 
 * <p>
 * Enforces rate limiting per tool and client IP address, and configures
 * execution
 * timeouts per tool or global defaults.
 */
public class McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);
    private static final String UNKNOWN_CLIENT_IP = "unknown";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final RestTemplate defaultRestTemplate;
    private final String baseUrl;
    private final Map<String, ToolExecutionMetadata> executionMetadata;
    private final RateLimitService rateLimitService;
    private final boolean rateLimitingEnabled;
    private final ToolConfigurationService toolConfigurationService;
    private final ExecutionTimeout defaultTimeout;
    private final ExecutionTimeout defaultConnectTimeout;
    private final SizeLimit defaultSizeLimit;
    private final AuditLogger auditLogger;
    private final boolean auditToolExecutions;
    private final boolean auditSecurityEvents;

    public McpToolExecutor(
            ApplicationContext applicationContext,
            ObjectMapper objectMapper,
            String baseUrl,
            RateLimitService rateLimitService,
            boolean rateLimitingEnabled,
            ToolConfigurationService toolConfigurationService,
            AutoMcpServerProperties properties,
            AuditLogger auditLogger) {
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.defaultRestTemplate = new RestTemplate();
        this.baseUrl = baseUrl;
        this.executionMetadata = new ConcurrentHashMap<>();
        this.rateLimitService = rateLimitService;
        this.rateLimitingEnabled = rateLimitingEnabled;
        this.toolConfigurationService = toolConfigurationService;
        this.defaultTimeout = ExecutionTimeout.parse(properties.execution().defaultTimeout());
        this.defaultSizeLimit = SizeLimit.parse(
                properties.execution().maxRequestBodySize(),
                properties.execution().maxResponseBodySize());
        this.defaultConnectTimeout = ExecutionTimeout.parse(properties.execution().defaultConnectTimeout());
        this.auditLogger = auditLogger;
        this.auditToolExecutions = properties.audit().logToolExecutions();
        this.auditSecurityEvents = properties.audit().logSecurityEvents();
    }

    /**
     * Register execution metadata for an HTTP-based tool.
     */
    public void registerToolMetadata(String toolName, ToolExecutionMetadata metadata) {
        executionMetadata.put(toolName, metadata);
        log.debug("Registered HTTP execution metadata for tool: {}", toolName);
    }

    /**
     * Execute a tool call with the given arguments via HTTP request.
     * Enforces rate limiting per tool and client IP.
     * Configures execution timeout per tool or uses global default.
     */
    public McpProtocol.CallToolResult executeTool(String toolName, Map<String, Object> arguments) {
        long startTime = System.currentTimeMillis();
        String clientIP = getClientIP();
        boolean success = false;
        String errorMessage = null;

        try {
            log.info("Executing tool: {} with arguments: {}", toolName, arguments);

            // Check rate limit (only if enabled)
            if (rateLimitingEnabled && !rateLimitService.allowRequest(toolName, clientIP)) {
                RateLimitService.RateLimitStatus status = rateLimitService.getStatus(toolName, clientIP);
                errorMessage = String.format(
                        "Rate limit exceeded for tool '%s'. Limit: %d requests per %s. Please try again later.",
                        toolName,
                        status.maxRequests(),
                        formatDuration(status.window()));
                log.warn("Rate limit exceeded for tool '{}' from IP '{}'", toolName, clientIP);

                // Audit rate limit exceeded
                if (auditSecurityEvents) {
                    auditLogger.logRateLimitExceeded(
                            toolName,
                            clientIP,
                            status.currentRequests(),
                            status.maxRequests());
                }

                return new McpProtocol.CallToolResult(
                        List.of(McpProtocol.Content.text(errorMessage)),
                        true);
            }

            // Execute HTTP request to the tool endpoint
            ToolExecutionMetadata metadata = executionMetadata.get(toolName);
            if (metadata == null) {
                errorMessage = "Tool execution metadata not available. Tool may require manual configuration.";
                log.warn("No execution metadata found for tool: {}", toolName);
                return new McpProtocol.CallToolResult(
                        List.of(McpProtocol.Content.text(errorMessage)),
                        true);
            }

            // Get timeout configuration for this tool
            ExecutionTimeout timeout = toolConfigurationService.getTimeoutConfig(toolName)
                    .orElse(defaultTimeout);
            ExecutionTimeout connectTimeout = defaultConnectTimeout;

            // Get size limit configuration for this tool
            SizeLimit sizeLimit = toolConfigurationService.getSizeLimitConfig(toolName)
                    .orElse(defaultSizeLimit);

            // Validate request body size
            try {
                validateRequestBodySize(arguments, sizeLimit, toolName);
            } catch (IllegalArgumentException e) {
                errorMessage = e.getMessage();

                // Audit size limit exceeded
                if (auditSecurityEvents) {
                    String json = objectMapper.writeValueAsString(arguments);
                    long actualSize = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    auditLogger.logSizeLimitExceeded(
                            toolName,
                            clientIP,
                            errorMessage,
                            actualSize,
                            sizeLimit.maxRequestBodyBytes());
                }

                throw e;
            }

            // Execute the HTTP request with timeout
            ResponseEntity<String> response = executeHttpRequest(metadata, arguments, timeout, connectTimeout);

            // Validate response body size
            try {
                validateResponseBodySize(response, sizeLimit, toolName);
            } catch (IllegalArgumentException e) {
                errorMessage = e.getMessage();

                // Audit size limit exceeded
                if (auditSecurityEvents) {
                    String body = response.getBody();
                    long actualSize = body != null
                            ? body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            : 0;
                    auditLogger.logSizeLimitExceeded(
                            toolName,
                            clientIP,
                            errorMessage,
                            actualSize,
                            sizeLimit.maxResponseBodyBytes());
                }

                throw e;
            }

            // Convert response to tool result
            McpProtocol.CallToolResult result = convertResponseToToolResult(response);
            success = !result.isError();

            // Audit successful execution
            if (auditToolExecutions) {
                long durationMs = System.currentTimeMillis() - startTime;
                auditLogger.logToolExecution(
                        toolName,
                        arguments,
                        clientIP,
                        success,
                        success ? null : "HTTP error: " + response.getStatusCode(),
                        durationMs);
            }

            return result;

        } catch (ResourceAccessException e) {
            // Handle timeout exceptions
            log.error("Timeout executing tool: {}", toolName, e);
            ExecutionTimeout timeout = toolConfigurationService.getTimeoutConfig(toolName)
                    .orElse(defaultTimeout);
            errorMessage = String.format(
                    "Request timeout for tool '%s'. Timeout limit: %s. The operation took too long to complete.",
                    toolName,
                    timeout.timeout());

            // Audit timeout event
            if (auditSecurityEvents) {
                auditLogger.logExecutionTimeout(
                        toolName,
                        clientIP,
                        errorMessage,
                        timeout.toMillis());
            }

            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text(errorMessage)),
                    true);
        } catch (Exception e) {
            log.error("Failed to execute tool: {}", toolName, e);
            errorMessage = "Error: " + e.getMessage();

            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text(errorMessage)),
                    true);
        } finally {
            // Audit failed execution (if not already audited as successful)
            if (!success && auditToolExecutions && errorMessage != null) {
                long durationMs = System.currentTimeMillis() - startTime;
                auditLogger.logToolExecution(
                        toolName,
                        arguments,
                        clientIP,
                        false,
                        errorMessage,
                        durationMs);
            }
        }
    }

    /**
     * Get client IP address from current HTTP request.
     * Checks common proxy headers first, falls back to remote address.
     */
    private String getClientIP() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // Check proxy headers
                String ip = request.getHeader(HEADER_X_FORWARDED_FOR);
                if (ip != null && !ip.isEmpty()) {
                    // X-Forwarded-For can contain multiple IPs, take the first one
                    return ip.split(",")[0].trim();
                }

                ip = request.getHeader(HEADER_X_REAL_IP);
                if (ip != null && !ip.isEmpty()) {
                    return ip;
                }

                // Fall back to remote address
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not determine client IP, using '{}'", UNKNOWN_CLIENT_IP, e);
        }

        return UNKNOWN_CLIENT_IP;
    }

    /**
     * Format duration for human-readable error messages.
     */
    private String formatDuration(java.time.Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds();

        if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }

    /**
     * Execute an HTTP request to an endpoint with configured timeouts.
     */
    private ResponseEntity<String> executeHttpRequest(
            ToolExecutionMetadata metadata,
            Map<String, Object> arguments,
            ExecutionTimeout timeout,
            ExecutionTimeout connectTimeout) {

        String url = buildUrl(metadata, arguments);
        HttpHeaders headers = buildHeaders(metadata, arguments);
        HttpEntity<?> entity = buildEntity(metadata, arguments, headers);

        // Create RestTemplate with timeout configuration
        RestTemplate restTemplate = createRestTemplateWithTimeout(timeout, connectTimeout);

        log.debug("Executing {} request to: {} (timeout: {}, connect timeout: {})",
                metadata.method(), url, timeout.timeout(), connectTimeout.timeout());
        return restTemplate.exchange(url, metadata.method(), entity, String.class);
    }

    /**
     * Create a RestTemplate with specific read and connect timeout values.
     */
    private RestTemplate createRestTemplateWithTimeout(ExecutionTimeout timeout, ExecutionTimeout connectTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(timeout.toMillis());
        factory.setConnectTimeout(connectTimeout.toMillis());
        return new RestTemplate(factory);
    }

    private String buildUrl(ToolExecutionMetadata metadata, Map<String, Object> arguments) {
        String path = metadata.endpoint();

        // Replace path variables
        for (Map.Entry<String, ToolExecutionMetadata.ParameterMapping> entry : metadata.parameterMappings()
                .entrySet()) {
            if (entry.getValue().location() == ToolExecutionMetadata.ParameterMapping.ParameterLocation.PATH) {
                String paramName = entry.getKey();
                Object value = arguments.get(paramName);
                if (value != null) {
                    path = path.replace("{" + paramName + "}", value.toString());
                }
            }
        }

        String url = baseUrl + path;

        // Add query parameters
        Map<String, Object> queryParams = extractQueryParams(metadata, arguments);
        if (!queryParams.isEmpty()) {
            StringBuilder query = new StringBuilder("?");
            queryParams.forEach((key, value) -> query.append(key).append("=").append(value).append("&"));
            url += query.substring(0, query.length() - 1);
        }

        return url;
    }

    private HttpHeaders buildHeaders(ToolExecutionMetadata metadata, Map<String, Object> arguments) {
        HttpHeaders headers = new HttpHeaders();

        // Set content type from metadata
        if (metadata.contentType() != null && !metadata.contentType().isEmpty()) {
            headers.setContentType(MediaType.parseMediaType(metadata.contentType()));
        } else {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        // Add header parameters
        for (Map.Entry<String, ToolExecutionMetadata.ParameterMapping> entry : metadata.parameterMappings()
                .entrySet()) {
            if (entry.getValue().location() == ToolExecutionMetadata.ParameterMapping.ParameterLocation.HEADER) {
                String paramName = entry.getKey();
                Object value = arguments.get(paramName);
                if (value != null) {
                    headers.add(paramName, value.toString());
                }
            }
        }

        return headers;
    }

    private HttpEntity<?> buildEntity(
            ToolExecutionMetadata metadata,
            Map<String, Object> arguments,
            HttpHeaders headers) {

        // Extract body parameters
        Object body = null;
        for (Map.Entry<String, ToolExecutionMetadata.ParameterMapping> entry : metadata.parameterMappings()
                .entrySet()) {
            if (entry.getValue().location() == ToolExecutionMetadata.ParameterMapping.ParameterLocation.BODY) {
                body = arguments.get(entry.getKey());
                break;
            }
        }

        if (body != null) {
            return new HttpEntity<>(body, headers);
        }
        return new HttpEntity<>(headers);
    }

    private Map<String, Object> extractQueryParams(
            ToolExecutionMetadata metadata,
            Map<String, Object> arguments) {
        Map<String, Object> params = new HashMap<>();

        for (Map.Entry<String, ToolExecutionMetadata.ParameterMapping> entry : metadata.parameterMappings()
                .entrySet()) {
            if (entry.getValue().location() == ToolExecutionMetadata.ParameterMapping.ParameterLocation.QUERY) {
                String paramName = entry.getKey();
                Object value = arguments.get(paramName);
                if (value != null) {
                    params.put(paramName, value);
                }
            }
        }

        return params;
    }

    private McpProtocol.CallToolResult convertResponseToToolResult(ResponseEntity<String> response) {
        List<McpProtocol.Content> content = new ArrayList<>();

        // Add response body as text or data
        String body = response.getBody();
        if (body != null && !body.isEmpty()) {
            try {
                // Try to parse as JSON
                JsonNode jsonNode = objectMapper.readTree(body);
                content.add(McpProtocol.Content.data(jsonNode));
            } catch (Exception e) {
                // If not JSON, add as text
                content.add(McpProtocol.Content.text(body));
            }
        }

        // Add status code info
        content.add(McpProtocol.Content.text("HTTP Status: " + response.getStatusCode()));

        // Determine if error based on status code
        boolean isError = response.getStatusCode().is4xxClientError() ||
                response.getStatusCode().is5xxServerError();

        return new McpProtocol.CallToolResult(content, isError);
    }

    /**
     * Validate request body size against configured limits.
     */
    private void validateRequestBodySize(Map<String, Object> arguments, SizeLimit sizeLimit, String toolName) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(arguments);
            long requestSizeBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

            if (requestSizeBytes > sizeLimit.maxRequestBodyBytes()) {
                String errorMessage = String.format(
                        "Request body size (%s) exceeds maximum allowed size (%s) for tool '%s'",
                        sizeLimit.formatBytes(requestSizeBytes),
                        sizeLimit.formatBytes(sizeLimit.maxRequestBodyBytes()),
                        toolName);
                throw new IllegalArgumentException(errorMessage);
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            log.warn("Could not validate request body size for tool: {}", toolName, e);
        }
    }

    /**
     * Validate response body size against configured limits.
     */
    private void validateResponseBodySize(ResponseEntity<String> response, SizeLimit sizeLimit, String toolName) {
        String body = response.getBody();
        if (body == null || body.isEmpty()) {
            return;
        }

        long responseSizeBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        if (responseSizeBytes > sizeLimit.maxResponseBodyBytes()) {
            String errorMessage = String.format(
                    "Response body size (%s) exceeds maximum allowed size (%s) for tool '%s'",
                    sizeLimit.formatBytes(responseSizeBytes),
                    sizeLimit.formatBytes(sizeLimit.maxResponseBodyBytes()),
                    toolName);
            throw new IllegalArgumentException(errorMessage);
        }
    }
}

package io.github.girisenji.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.girisenji.ai.mcp.McpProtocol;
import io.github.girisenji.ai.model.ToolExecutionMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for executing MCP tool calls by invoking the actual API endpoints.
 * Stores execution metadata and performs HTTP requests to discovered endpoints.
 */
public class McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final Map<String, ToolExecutionMetadata> executionMetadata;

    public McpToolExecutor(
            ApplicationContext applicationContext,
            ObjectMapper objectMapper,
            String baseUrl) {
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.baseUrl = baseUrl;
        this.executionMetadata = new ConcurrentHashMap<>();
    }

    /**
     * Register execution metadata for a tool.
     */
    public void registerToolMetadata(String toolName, ToolExecutionMetadata metadata) {
        executionMetadata.put(toolName, metadata);
        log.debug("Registered execution metadata for tool: {}", toolName);
    }

    /**
     * Execute a tool call with the given arguments.
     */
    public McpProtocol.CallToolResult executeTool(String toolName, Map<String, Object> arguments) {
        try {
            log.info("Executing tool: {} with arguments: {}", toolName, arguments);

            ToolExecutionMetadata metadata = executionMetadata.get(toolName);
            if (metadata == null) {
                log.warn("No execution metadata found for tool: {}", toolName);
                return new McpProtocol.CallToolResult(
                        List.of(McpProtocol.Content.text(
                                "Tool execution metadata not available. Tool may require manual configuration.")),
                        true);
            }

            // Execute the HTTP request
            ResponseEntity<String> response = executeHttpRequest(metadata, arguments);

            // Convert response to tool result
            return convertResponseToToolResult(response);

        } catch (Exception e) {
            log.error("Failed to execute tool: {}", toolName, e);
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: " + e.getMessage())),
                    true);
        }
    }

    /**
     * Execute an HTTP request to an endpoint.
     */
    private ResponseEntity<String> executeHttpRequest(
            ToolExecutionMetadata metadata,
            Map<String, Object> arguments) {

        String url = buildUrl(metadata, arguments);
        HttpHeaders headers = buildHeaders(metadata, arguments);
        HttpEntity<?> entity = buildEntity(metadata, arguments, headers);

        log.debug("Executing {} request to: {}", metadata.method(), url);
        return restTemplate.exchange(url, metadata.method(), entity, String.class);
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
}

package com.girisenji.ai.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisenji.ai.config.AutoMcpServerProperties;
import com.girisenji.ai.mcp.McpProtocol;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Discovers API endpoints from OpenAPI specification and converts them to MCP
 * tools.
 */
public class OpenApiDiscoveryService implements EndpointDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiDiscoveryService.class);

    private final AutoMcpServerProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher;
    private final OpenAPI openAPI;

    public OpenApiDiscoveryService(
            AutoMcpServerProperties properties,
            ObjectMapper objectMapper,
            OpenAPI openAPI) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.pathMatcher = new AntPathMatcher();
        this.openAPI = openAPI;
    }

    @Override
    public List<McpProtocol.Tool> discoverTools() {
        if (openAPI == null || openAPI.getPaths() == null) {
            log.debug("No OpenAPI specification found");
            return Collections.emptyList();
        }

        List<McpProtocol.Tool> tools = new ArrayList<>();

        openAPI.getPaths().forEach((path, pathItem) -> {
            if (shouldIncludePath(path)) {
                tools.addAll(convertPathItemToTools(path, pathItem));
            }
        });

        log.info("Discovered {} tools from OpenAPI specification", tools.size());
        return tools;
    }

    @Override
    public String getDiscoveryType() {
        return "OpenAPI";
    }

    @Override
    public boolean isEnabled() {
        return properties.discovery().openapiEnabled() && openAPI != null;
    }

    private List<McpProtocol.Tool> convertPathItemToTools(String path, PathItem pathItem) {
        List<McpProtocol.Tool> tools = new ArrayList<>();

        Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
        operations.forEach((method, operation) -> {
            if (operation != null) {
                McpProtocol.Tool tool = convertOperationToTool(path, method.name(), operation);
                if (tool != null) {
                    tools.add(tool);
                }
            }
        });

        return tools;
    }

    private McpProtocol.Tool convertOperationToTool(String path, String method, Operation operation) {
        try {
            String toolName = generateToolName(path, method, operation);
            String description = generateDescription(operation, path, method);
            JsonNode inputSchema = generateInputSchema(operation);

            return new McpProtocol.Tool(toolName, description, inputSchema);
        } catch (Exception e) {
            log.warn("Failed to convert operation to tool: {} {}", method, path, e);
            return null;
        }
    }

    private String generateToolName(String path, String method, Operation operation) {
        // Use operationId if available and configured
        if (properties.tools().useOperationIdAsToolName() &&
                operation.getOperationId() != null &&
                !operation.getOperationId().isEmpty()) {
            return sanitizeToolName(operation.getOperationId());
        }

        // Generate from path and method
        String cleaned = path.replaceAll("\\{[^}]+\\}", "")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        String toolName = method.toLowerCase() + "_" + cleaned;

        return sanitizeToolName(toolName);
    }

    private String sanitizeToolName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        int maxLength = properties.tools().maxToolNameLength();
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }

        return sanitized;
    }

    private String generateDescription(Operation operation, String path, String method) {
        StringBuilder desc = new StringBuilder();

        if (operation.getSummary() != null && !operation.getSummary().isEmpty()) {
            desc.append(operation.getSummary());
        } else if (operation.getDescription() != null && !operation.getDescription().isEmpty()) {
            desc.append(operation.getDescription());
        } else {
            desc.append(method.toUpperCase()).append(" ").append(path);
        }

        return desc.toString();
    }

    private JsonNode generateInputSchema(Operation operation) {
        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        var properties = objectMapper.createObjectNode();
        var required = objectMapper.createArrayNode();

        // Add path parameters
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if (param.getSchema() != null) {
                    JsonNode paramSchema = convertSchemaToJson(param.getSchema());
                    properties.set(param.getName(), paramSchema);

                    if (Boolean.TRUE.equals(param.getRequired())) {
                        required.add(param.getName());
                    }
                }
            }
        }

        // Add request body
        if (operation.getRequestBody() != null) {
            RequestBody requestBody = operation.getRequestBody();
            Content content = requestBody.getContent();
            if (content != null && content.get("application/json") != null) {
                MediaType mediaType = content.get("application/json");
                if (mediaType.getSchema() != null) {
                    JsonNode bodySchema = convertSchemaToJson(mediaType.getSchema());
                    properties.set("body", bodySchema);

                    if (Boolean.TRUE.equals(requestBody.getRequired())) {
                        required.add("body");
                    }
                }
            }
        }

        schema.set("properties", properties);
        if (required.size() > 0) {
            schema.set("required", required);
        }

        return schema;
    }

    private JsonNode convertSchemaToJson(Schema<?> schema) {
        try {
            // Convert OpenAPI schema to JSON
            String json = objectMapper.writeValueAsString(schema);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to convert schema to JSON", e);
            return objectMapper.createObjectNode();
        }
    }

    private boolean shouldIncludePath(String path) {
        // Check exclude patterns first
        for (String pattern : properties.tools().excludePatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return false;
            }
        }

        // Check include patterns
        for (String pattern : properties.tools().includePatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }

        return false;
    }
}

package io.github.girisenji.ai.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.girisenji.ai.config.AutoMcpServerProperties;
import io.github.girisenji.ai.mcp.McpProtocol;
import io.github.girisenji.ai.util.JsonSchemaGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Discovers REST endpoints from Spring MVC controllers and converts them to MCP
 * tools.
 */
public class RestEndpointDiscoveryService implements EndpointDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(RestEndpointDiscoveryService.class);

    private final AutoMcpServerProperties properties;
    private final ApplicationContext applicationContext;
    private final JsonSchemaGenerator schemaGenerator;
    private final AntPathMatcher pathMatcher;

    public RestEndpointDiscoveryService(
            AutoMcpServerProperties properties,
            ApplicationContext applicationContext,
            JsonSchemaGenerator schemaGenerator) {
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.schemaGenerator = schemaGenerator;
        this.pathMatcher = new AntPathMatcher();
    }

    @Override
    public List<McpProtocol.Tool> discoverTools() {
        List<McpProtocol.Tool> tools = new ArrayList<>();

        try {
            RequestMappingHandlerMapping handlerMapping = applicationContext
                    .getBean(RequestMappingHandlerMapping.class);

            Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();

            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
                RequestMappingInfo mappingInfo = entry.getKey();
                HandlerMethod handlerMethod = entry.getValue();

                if (shouldIncludeMapping(mappingInfo)) {
                    McpProtocol.Tool tool = convertHandlerToTool(mappingInfo, handlerMethod);
                    if (tool != null) {
                        tools.add(tool);
                    }
                }
            }

            log.info("Discovered {} tools from REST endpoints", tools.size());
        } catch (Exception e) {
            log.error("Failed to discover REST endpoints", e);
        }

        return tools;
    }

    @Override
    public String getDiscoveryType() {
        return "REST";
    }

    @Override
    public boolean isEnabled() {
        return properties.discovery().restEnabled();
    }

    private McpProtocol.Tool convertHandlerToTool(
            RequestMappingInfo mappingInfo,
            HandlerMethod handlerMethod) {
        try {
            String toolName = generateToolName(mappingInfo, handlerMethod);
            String description = generateDescription(mappingInfo, handlerMethod);
            JsonNode inputSchema = generateInputSchema(handlerMethod.getMethod());

            return new McpProtocol.Tool(toolName, description, inputSchema);
        } catch (Exception e) {
            log.warn("Failed to convert handler to tool: {}", handlerMethod, e);
            return null;
        }
    }

    private String generateToolName(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        // Get the first path pattern
        String path = mappingInfo.getPathPatternsCondition() != null &&
                !mappingInfo.getPathPatternsCondition().getPatterns().isEmpty()
                        ? mappingInfo.getPathPatternsCondition().getPatterns().iterator().next().getPatternString()
                        : "";

        // Get the first HTTP method
        String method = mappingInfo.getMethodsCondition() != null &&
                !mappingInfo.getMethodsCondition().getMethods().isEmpty()
                        ? mappingInfo.getMethodsCondition().getMethods().iterator().next().name().toLowerCase()
                        : "request";

        // Clean path
        String cleaned = path.replaceAll("\\{[^}]+\\}", "")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        String toolName = method + "_" + cleaned;
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

    private String generateDescription(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        StringBuilder desc = new StringBuilder();

        // Get HTTP method
        Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
        if (!methods.isEmpty()) {
            desc.append(methods.iterator().next().name()).append(" ");
        }

        // Get path
        if (mappingInfo.getPathPatternsCondition() != null &&
                !mappingInfo.getPathPatternsCondition().getPatterns().isEmpty()) {
            desc.append(mappingInfo.getPathPatternsCondition()
                    .getPatterns()
                    .iterator()
                    .next()
                    .getPatternString());
        }

        // Add controller and method info
        desc.append(" - ")
                .append(handlerMethod.getBeanType().getSimpleName())
                .append(".")
                .append(handlerMethod.getMethod().getName());

        return desc.toString();
    }

    private JsonNode generateInputSchema(Method method) {
        return schemaGenerator.generateSchemaForMethod(method);
    }

    private boolean shouldIncludeMapping(RequestMappingInfo mappingInfo) {
        if (mappingInfo.getPathPatternsCondition() == null) {
            return false;
        }

        for (var pattern : mappingInfo.getPathPatternsCondition().getPatterns()) {
            String path = pattern.getPatternString();

            // Check exclude patterns first
            for (String excludePattern : properties.tools().excludePatterns()) {
                if (pathMatcher.match(excludePattern, path)) {
                    if (properties.tools().logExcludedTools()) {
                        log.debug("Excluding endpoint {} (matched exclude pattern: {})", path, excludePattern);
                    }
                    return false;
                }
            }

            // Check include patterns
            for (String includePattern : properties.tools().includePatterns()) {
                if (pathMatcher.match(includePattern, path)) {
                    return true;
                }
            }
        }

        if (properties.tools().logExcludedTools()) {
            for (var pattern : mappingInfo.getPathPatternsCondition().getPatterns()) {
                log.debug("Excluding endpoint {} (no matching include pattern)", pattern.getPatternString());
            }
        }
        return false;
    }
}

package io.github.girisenji.ai.service;

import io.github.girisenji.ai.mcp.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing MCP Resources.
 * Resources allow exposing data/content that can be read by AI clients.
 * 
 * <p>
 * Examples of resources:
 * <ul>
 * <li>REST endpoint responses (GET /api/users → user list resource)</li>
 * <li>OpenAPI specification files</li>
 * <li>Configuration files</li>
 * <li>Database query results</li>
 * </ul>
 */
@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final Map<String, McpProtocol.Resource> resources;
    private final Map<String, ResourceProvider> resourceProviders;

    public ResourceService() {
        this.resources = new ConcurrentHashMap<>();
        this.resourceProviders = new ConcurrentHashMap<>();
        initializeDefaultResources();
    }

    /**
     * Initialize default resources exposed by the MCP server.
     */
    private void initializeDefaultResources() {
        // Expose server configuration as a resource
        registerResource(
                "mcp://server/config",
                "Server Configuration",
                "Current  MCP server configuration and capabilities",
                "application/json",
                () -> {
                    String configText = """
                            {
                              "protocol": "MCP 2024-11-05",
                              "features": ["tools", "resources", "prompts"],
                              "transport": "SSE"
                            }
                            """;
                    return McpProtocol.ResourceContents.text(
                            "mcp://server/config",
                            "application/json",
                            configText);
                });

        log.info("Initialized {} default resources", resources.size());
    }

    /**
     * Register a resource with a provider function.
     * 
     * @param uri         Unique URI for the resource (e.g., "mcp://server/users")
     * @param name        Human-readable name
     * @param description Description of the resource
     * @param mimeType    MIME type (e.g., "application/json", "text/plain")
     * @param provider    Function that provides the resource contents when accessed
     */
    public void registerResource(
            String uri,
            String name,
            String description,
            String mimeType,
            ResourceProvider provider) {

        McpProtocol.Resource resource = new McpProtocol.Resource(uri, name, description, mimeType);
        resources.put(uri, resource);
        resourceProviders.put(uri, provider);
        log.debug("Registered resource: {} ({})", name, uri);
    }

    /**
     * Get all available resources.
     * 
     * @return List of resource definitions
     */
    public List<McpProtocol.Resource> listResources() {
        return new ArrayList<>(resources.values());
    }

    /**
     * Read the contents of a specific resource.
     * 
     * @param uri Resource URI to read
     * @return Resource contents
     * @throws IllegalArgumentException if resource not found
     */
    public McpProtocol.ResourceContents readResource(String uri) {
        ResourceProvider provider = resourceProviders.get(uri);
        if (provider == null) {
            throw new IllegalArgumentException("Resource not found: " + uri);
        }

        try {
            log.debug("Reading resource: {}", uri);
            return provider.provide();
        } catch (Exception e) {
            log.error("Failed to read resource: {}", uri, e);
            throw new RuntimeException("Failed to read resource: " + e.getMessage(), e);
        }
    }

    /**
     * Functional interface for resource providers.
     */
    @FunctionalInterface
    public interface ResourceProvider {
        McpProtocol.ResourceContents provide();
    }
}

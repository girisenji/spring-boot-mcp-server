package io.github.girisenji.ai.discovery;

import io.github.girisenji.ai.mcp.McpProtocol;

import java.util.List;

/**
 * Service for discovering API endpoints and converting them to MCP tools.
 */
public interface EndpointDiscoveryService {

    /**
     * Discover all endpoints and convert them to MCP tools.
     * 
     * @return List of MCP tools
     */
    List<McpProtocol.Tool> discoverTools();

    /**
     * Get the discovery type name.
     * 
     * @return Discovery type (e.g., "OpenAPI", "REST", "GraphQL")
     */
    String getDiscoveryType();

    /**
     * Check if this discovery service is enabled and available.
     * 
     * @return true if enabled and required dependencies are available
     */
    boolean isEnabled();
}

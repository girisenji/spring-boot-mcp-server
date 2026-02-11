package io.github.girisenji.ai.service;

import io.github.girisenji.ai.config.AutoMcpServerProperties;
import io.github.girisenji.ai.discovery.EndpointDiscoveryService;
import io.github.girisenji.ai.mcp.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for managing MCP tools discovered from various sources.
 * Works with ToolApprovalService to only expose approved tools.
 * Thread-safe implementation with proper double-checked locking.
 */
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final List<EndpointDiscoveryService> discoveryServices;
    private final ToolApprovalService approvalService;
    private final AutoMcpServerProperties properties;
    private final Map<String, McpProtocol.Tool> discoveredTools;
    private final Object initLock = new Object();
    private volatile boolean initialized = false;

    public McpToolRegistry(
            List<EndpointDiscoveryService> discoveryServices,
            ToolApprovalService approvalService,
            AutoMcpServerProperties properties) {
        this.discoveryServices = discoveryServices != null ? discoveryServices : Collections.emptyList();
        this.approvalService = approvalService;
        this.properties = properties;
        this.discoveredTools = new ConcurrentHashMap<>();
        log.info("MCP tool registry created with {} discovery services", this.discoveryServices.size());
    }

    /**
     * Refresh the tool cache by rediscovering all tools.
     */
    public synchronized void refreshTools() {
        discoveredTools.clear();
        AutoMcpServerProperties.Tools.DuplicateToolStrategy strategy = properties.tools().duplicateToolStrategy();

        for (EndpointDiscoveryService service : discoveryServices) {
            if (service.isEnabled()) {
                try {
                    log.debug("Discovering tools using {}", service.getDiscoveryType());
                    List<McpProtocol.Tool> tools = service.discoverTools();

                    for (McpProtocol.Tool tool : tools) {
                        String key = tool.name();
                        boolean isDuplicate = discoveredTools.containsKey(key);

                        if (isDuplicate) {
                            handleDuplicateTool(key, tool, strategy);
                        } else {
                            discoveredTools.put(key, tool);
                        }
                    }

                    log.info("Discovered {} tools from {}", tools.size(), service.getDiscoveryType());
                } catch (Exception e) {
                    log.error("Failed to discover tools using {}", service.getDiscoveryType(), e);
                }
            } else {
                log.debug("Discovery service {} is disabled", service.getDiscoveryType());
            }
        }

        // Log approval summary
        long approvedCount = discoveredTools.keySet().stream()
                .filter(approvalService::isToolApproved)
                .count();
        log.info("Total discovered: {}, Approved: {}", discoveredTools.size(), approvedCount);
    }

    /**
     * Handle duplicate tool based on configured strategy.
     */
    private void handleDuplicateTool(
            String toolName,
            McpProtocol.Tool newTool,
            AutoMcpServerProperties.Tools.DuplicateToolStrategy strategy) {
        switch (strategy) {
            case FIRST_WINS:
                log.warn("Duplicate tool name detected: {}. Keeping first occurrence (strategy: FIRST_WINS)", toolName);
                break;
            case LAST_WINS:
                log.warn("Duplicate tool name detected: {}. Overwriting with new occurrence (strategy: LAST_WINS)",
                        toolName);
                discoveredTools.put(toolName, newTool);
                break;
            case FAIL_ON_DUPLICATE:
                throw new IllegalStateException(
                        String.format("Duplicate tool name detected: %s (strategy: FAIL_ON_DUPLICATE)", toolName));
        }
    }

    /**
     * Get all APPROVED tools (only these are exposed to agents).
     */
    public List<McpProtocol.Tool> getAllTools() {
        if (!initialized) {
            initialize();
        }
        return getApprovedToolsList();
    }

    /**
     * Get all discovered tools (regardless of approval status).
     */
    public List<McpProtocol.Tool> getAllDiscoveredTools() {
        if (!initialized) {
            initialize();
        }
        return new ArrayList<>(discoveredTools.values());
    }

    /**
     * Get a tool by name (only if approved).
     */
    public Optional<McpProtocol.Tool> getTool(String name) {
        if (!initialized) {
            initialize();
        }
        if (approvalService.isToolApproved(name)) {
            return Optional.ofNullable(discoveredTools.get(name));
        }
        return Optional.empty();
    }

    /**
     * Get the count of APPROVED tools.
     */
    public int getToolCount() {
        if (!initialized) {
            initialize();
        }
        return approvalService.getApprovedCount();
    }

    /**
     * Get the count of all discovered tools.
     */
    public int getDiscoveredToolCount() {
        if (!initialized) {
            initialize();
        }
        return discoveredTools.size();
    }

    /**
     * Check if a tool exists and is approved.
     */
    public boolean hasTool(String name) {
        if (!initialized) {
            initialize();
        }
        return discoveredTools.containsKey(name) && approvalService.isToolApproved(name);
    }

    /**
     * Get approved tools with pagination support.
     */
    public List<McpProtocol.Tool> getTools(int offset, int limit) {
        if (!initialized) {
            initialize();
        }

        return getApprovedToolsList().stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<McpProtocol.Tool> getApprovedToolsList() {
        return discoveredTools.entrySet().stream()
                .filter(entry -> approvalService.isToolApproved(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Check if the registry is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Initialize the registry by discovering all tools.
     * Thread-safe implementation with double-checked locking.
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        synchronized (initLock) {
            if (!initialized) {
                log.info("Initializing MCP tool registry...");
                refreshTools();
                initialized = true;
                log.info("MCP tool registry initialized with {} discovered tools, {} approved",
                        discoveredTools.size(), getToolCount());
            }
        }
    }
}

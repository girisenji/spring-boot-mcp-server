package io.github.girisenji.ai.discovery;

import io.github.girisenji.ai.config.AutoMcpServerProperties;
import io.github.girisenji.ai.mcp.McpProtocol;
import io.github.girisenji.ai.model.McpTool;
import io.github.girisenji.ai.service.McpToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Discovery service for custom McpTool implementations.
 * Scans Spring context for beans implementing the McpTool interface
 * and registers them for direct Java execution.
 */
public class CustomToolDiscoveryService implements EndpointDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(CustomToolDiscoveryService.class);

    private final ApplicationContext applicationContext;
    private final McpToolExecutor toolExecutor;
    private final AutoMcpServerProperties properties;

    public CustomToolDiscoveryService(
            ApplicationContext applicationContext,
            McpToolExecutor toolExecutor,
            AutoMcpServerProperties properties) {
        this.applicationContext = applicationContext;
        this.toolExecutor = toolExecutor;
        this.properties = properties;
    }

    @Override
    public List<McpProtocol.Tool> discoverTools() {
        log.info("Discovering custom McpTool implementations...");

        Collection<McpTool> customTools = applicationContext.getBeansOfType(McpTool.class).values();
        List<McpProtocol.Tool> tools = new ArrayList<>();

        for (McpTool customTool : customTools) {
            try {
                String name = customTool.getName();
                String description = customTool.getDescription();

                // Create MCP Tool definition
                McpProtocol.Tool tool = new McpProtocol.Tool(
                        name,
                        description,
                        customTool.getInputSchema());

                tools.add(tool);

                // Register for custom execution
                toolExecutor.registerCustomTool(name, customTool);

                log.debug("Discovered custom tool: {} - {}", name, description);

            } catch (Exception e) {
                log.error("Failed to register custom tool: {}", customTool.getClass().getName(), e);
            }
        }

        log.info("Discovered {} custom tool(s)", tools.size());
        return tools;
    }

    @Override
    public String getDiscoveryType() {
        return "custom-tools";
    }

    @Override
    public boolean isEnabled() {
        // Custom tools are always enabled if beans are present
        return true;
    }
}

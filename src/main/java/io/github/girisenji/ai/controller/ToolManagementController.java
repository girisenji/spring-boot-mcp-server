package io.github.girisenji.ai.controller;

import io.github.girisenji.ai.mcp.McpProtocol;
import io.github.girisenji.ai.service.AuditLogger;
import io.github.girisenji.ai.service.McpToolRegistry;
import io.github.girisenji.ai.service.ToolConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Management controller for tool discovery.
 * 
 * <p>
 * Provides visibility into discovered tools and helps generate
 * approved-tools.yml configuration.
 * 
 * <p>
 * <b>Development Workflow:</b>
 * <ol>
 * <li>Run application with discovery enabled</li>
 * <li>Call GET /mcp/admin/tools/discovered to see all discovered tools</li>
 * <li>Call GET /mcp/admin/tools/yaml to get YAML format for
 * approved-tools.yml</li>
 * <li>Copy desired tools to approved-tools.yml in your application</li>
 * <li>Restart application - only approved tools will be exposed</li>
 * </ol>
 */
@RestController
@RequestMapping("${auto-mcp-server.endpoint:/mcp}/admin/tools")
@Tag(name = "Tool Discovery", description = "Discover tools and generate configuration")
public class ToolManagementController {

    private static final String UNKNOWN_CLIENT_IP = "unknown";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private final McpToolRegistry toolRegistry;
    private final ToolConfigurationService toolConfigService;
    private final AuditLogger auditLogger;

    public ToolManagementController(
            McpToolRegistry toolRegistry,
            ToolConfigurationService toolConfigService,
            AuditLogger auditLogger) {
        this.toolRegistry = toolRegistry;
        this.toolConfigService = toolConfigService;
        this.auditLogger = auditLogger;
    }

    /**
     * Get summary of tool discovery and approval status.
     */
    @GetMapping("/summary")
    @Operation(summary = "Get discovery summary", description = "Summary statistics of tool discovery and approval")
    public Map<String, Object> getSummary() {
        List<McpProtocol.Tool> allTools = toolRegistry.getAllDiscoveredTools();
        int approvedCount = toolConfigService.getApprovedCount();

        return Map.of(
                "totalDiscovered", allTools.size(),
                "totalApproved", approvedCount,
                "unapproved", allTools.size() - approvedCount,
                "approvedTools", toolConfigService.getApprovedToolNames());
    }

    /**
     * List all discovered tools.
     */
    @GetMapping("/discovered")
    @Operation(summary = "List all discovered tools", description = "Shows all tools found during discovery")
    public DiscoveryResponse getDiscoveredTools() {
        List<McpProtocol.Tool> allTools = toolRegistry.getAllDiscoveredTools();

        List<ToolInfo> tools = allTools.stream()
                .map(tool -> new ToolInfo(
                        tool.name(),
                        tool.description(),
                        toolConfigService.isToolApproved(tool.name())))
                .collect(Collectors.toList());

        return new DiscoveryResponse(tools.size(), toolConfigService.getApprovedCount(), tools);
    }

    /**
     * Get discovered tools in YAML format for approved-tools.yml.
     * 
     * <p>
     * Copy this output to your application's approved-tools.yml file.
     */
    @GetMapping(value = "/yaml", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get YAML for approved-tools.yml", description = "Generate YAML configuration with all discovered tools")
    public ResponseEntity<String> getDiscoveredToolsAsYaml(
            @RequestParam(defaultValue = "false") boolean approvedOnly) {
        List<McpProtocol.Tool> tools = toolRegistry.getAllDiscoveredTools();

        StringBuilder yaml = new StringBuilder();
        yaml.append("# Auto MCP Server - Approved Tools Configuration\n");
        yaml.append("# Generated from discovered endpoints\n");
        yaml.append("# Copy this to your application's src/main/resources/approved-tools.yml\n");
        yaml.append("#\n");
        yaml.append("# Configuration:\n");
        yaml.append("#   auto-mcp-server:\n");
        yaml.append("#     tools:\n");
        yaml.append("#       approval-config-file: classpath:approved-tools.yml\n");
        yaml.append("\n");
        yaml.append("approvedTools:\n");

        if (tools.isEmpty()) {
            yaml.append("  [] # No tools discovered\n");
        } else {
            for (McpProtocol.Tool tool : tools) {
                boolean isApproved = toolConfigService.isToolApproved(tool.name());

                if (approvedOnly && !isApproved) {
                    continue;
                }

                if (tool.description() != null && !tool.description().isEmpty()) {
                    yaml.append("  # ").append(tool.description()).append("\n");
                }

                if (approvedOnly || isApproved) {
                    yaml.append("  - ").append(tool.name()).append("\n");
                } else {
                    yaml.append("  # - ").append(tool.name()).append(" # Commented - review before approving\n");
                }
            }
        }

        yaml.append("\n# Total discovered: ").append(tools.size()).append("\n");
        yaml.append("# Currently approved: ").append(toolConfigService.getApprovedCount()).append("\n");

        return ResponseEntity.ok(yaml.toString());
    }

    /**
     * Refresh tool discovery (re-scan all endpoints).
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh tool discovery", description = "Re-scan application for new endpoints")
    public Map<String, Object> refreshDiscovery() {
        String clientIP = getClientIP();

        // Log config reload action (audit as approval change since it affects what
        // tools are available)
        auditLogger.logApprovalChange(
                "system",
                clientIP,
                true,
                "Tool discovery refresh triggered");

        toolRegistry.refreshTools();
        return Map.of(
                "message", "Tool discovery refreshed",
                "summary", getSummary());
    }

    /**
     * Reload approved tools from approved-tools.yml configuration file.
     * 
     * <p>
     * Allows updating tool approvals without restarting the application:
     * <ol>
     * <li>Edit approved-tools.yml file</li>
     * <li>Call this endpoint to reload configuration</li>
     * <li>Changes take effect immediately</li>
     * </ol>
     */
    @PostMapping("/reload")
    @Operation(summary = "Reload approved tools from YAML", description = "Reload approved-tools.yml without restarting")
    public Map<String, Object> reloadConfiguration() {
        String clientIP = getClientIP();

        // Capture old state for audit
        int oldApprovedCount = toolConfigService.getApprovedCount();

        // Reload configuration
        toolConfigService.reloadApprovedTools();

        // Capture new state
        int newApprovedCount = toolConfigService.getApprovedCount();

        // Log the reload action
        auditLogger.logApprovalChange(
                "approved-tools.yml",
                clientIP,
                true,
                String.format("Configuration reloaded: %d → %d approved tools", oldApprovedCount, newApprovedCount));

        return Map.of(
                "message", "Approved tools configuration reloaded successfully",
                "previousCount", oldApprovedCount,
                "currentCount", newApprovedCount,
                "summary", getSummary());
    }

    /**
     * Get client IP address from current HTTP request.
     */
    private String getClientIP() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return UNKNOWN_CLIENT_IP;
        }

        HttpServletRequest request = attributes.getRequest();

        // Check X-Forwarded-For header (for proxied requests)
        String forwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        // Check X-Real-IP header
        String realIP = request.getHeader(HEADER_X_REAL_IP);
        if (realIP != null && !realIP.isEmpty()) {
            return realIP;
        }

        // Fall back to remote address
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : UNKNOWN_CLIENT_IP;
    }

    // Response DTOs

    /**
     * Response containing discovery statistics and tool information.
     *
     * @param totalDiscovered Total number of tools discovered
     * @param totalApproved   Total number of approved tools from configuration
     * @param tools           List of tool information
     */
    public record DiscoveryResponse(
            int totalDiscovered,
            int totalApproved,
            List<ToolInfo> tools) {
    }

    /**
     * Information about a single discovered tool.
     *
     * @param name        Tool name
     * @param description Tool description
     * @param approved    Whether this tool is in the allowed-list configuration
     */
    public record ToolInfo(
            String name,
            String description,
            boolean approved) {
    }
}

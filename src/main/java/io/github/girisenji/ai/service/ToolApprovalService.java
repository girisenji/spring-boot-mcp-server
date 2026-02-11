package io.github.girisenji.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.girisenji.ai.config.AutoMcpServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing tool approval.
 * 
 * <p>
 * Loads approved tools from YAML configuration file. This is a config-driven
 * approach
 * where approved tools are versioned in Git, declarative, and survive restarts.
 * 
 * <p>
 * Configuration file format (approved-tools.yml):
 * 
 * <pre>
 * approvedTools:
 *   - getUser
 *   - listUsers
 *   - searchProducts
 * </pre>
 */
public class ToolApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ToolApprovalService.class);

    private final AutoMcpServerProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper;
    private final Set<String> approvedTools;

    public ToolApprovalService(
            AutoMcpServerProperties properties,
            ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.approvedTools = ConcurrentHashMap.newKeySet();

        loadApprovedTools();
    }

    /**
     * Check if a tool is approved.
     */
    public boolean isToolApproved(String toolName) {
        return approvedTools.contains(toolName);
    }

    /**
     * Get all approved tool names.
     */
    public Set<String> getApprovedToolNames() {
        return Collections.unmodifiableSet(approvedTools);
    }

    /**
     * Get count of approved tools.
     */
    public int getApprovedCount() {
        return approvedTools.size();
    }

    /**
     * Reload approved tools from configuration file.
     */
    public void reloadApprovedTools() {
        approvedTools.clear();
        loadApprovedTools();
    }

    private void loadApprovedTools() {
        String configFile = properties.tools().approvalConfigFile();
        if (configFile == null || configFile.isEmpty()) {
            log.warn("No approval config file specified. No tools will be approved.");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(configFile);
            if (!resource.exists()) {
                log.warn("Approval config file not found: {}. No tools will be approved.", configFile);
                return;
            }

            ApprovalConfig config = yamlMapper.readValue(
                    resource.getInputStream(),
                    ApprovalConfig.class);

            if (config.approvedTools() == null || config.approvedTools().isEmpty()) {
                log.warn("Approval config file is empty: {}. No tools will be approved.", configFile);
                return;
            }

            approvedTools.addAll(config.approvedTools());
            log.info("Loaded {} approved tools from config file: {}", approvedTools.size(), configFile);

        } catch (IOException e) {
            log.error("Failed to load approval config from: {}. No tools will be approved.", configFile, e);
        }
    }

    /**
     * Configuration file format for approved tools.
     */
    private record ApprovalConfig(Set<String> approvedTools) {
        public ApprovalConfig {
            if (approvedTools == null) {
                approvedTools = new HashSet<>();
            }
        }
    }
}

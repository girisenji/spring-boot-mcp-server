package io.github.girisenji.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.girisenji.ai.config.AutoMcpServerProperties;
import io.github.girisenji.ai.model.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing tool configuration including allowed-list and rate
 * limits.
 * 
 * <p>
 * Loads tool configurations from YAML file. This is a config-driven approach
 * where tool access control and rate limits are versioned in Git, declarative,
 * and survive restarts.
 * 
 * <p>
 * Configuration file format (approved-tools.yml):
 * 
 * <pre>
 * approvedTools:
 *   # Simple format (uses default rate limit)
 *   - getUser
 *   
 *   # With custom rate limit
 *   - name: listUsers
 *     rateLimit:
 *       requests: 100
 *       window: PT1H  # ISO-8601 duration
 * </pre>
 */
public class ToolConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ToolConfigurationService.class);

    private final AutoMcpServerProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper;
    private final Set<String> approvedTools;
    private final Map<String, RateLimitConfig> rateLimitConfigs;
    private final RateLimitService rateLimitService;

    public ToolConfigurationService(
            AutoMcpServerProperties properties,
            ResourceLoader resourceLoader,
            RateLimitService rateLimitService) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.rateLimitService = rateLimitService;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.approvedTools = ConcurrentHashMap.newKeySet();
        this.rateLimitConfigs = new ConcurrentHashMap<>();

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
     * Get rate limit configuration for a specific tool.
     */
    public Optional<RateLimitConfig> getRateLimitConfig(String toolName) {
        return Optional.ofNullable(rateLimitConfigs.get(toolName));
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
        rateLimitConfigs.clear();
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

            // Process approved tools and their rate limit configurations
            int toolsWithRateLimits = 0;
            for (ToolConfig toolConfig : config.approvedTools()) {
                String toolName = toolConfig.getName();
                approvedTools.add(toolName);

                if (toolConfig.getRateLimit() != null) {
                    RateLimitConfig rateLimitConfig = RateLimitConfig.parse(
                            toolConfig.getRateLimit().requests(),
                            toolConfig.getRateLimit().window());
                    rateLimitConfigs.put(toolName, rateLimitConfig);
                    rateLimitService.registerRateLimit(toolName, rateLimitConfig);
                    toolsWithRateLimits++;
                }
            }

            log.info("Loaded {} approved tools from config file: {} ({} with custom rate limits)",
                    approvedTools.size(), configFile, toolsWithRateLimits);

        } catch (IOException e) {
            log.error("Failed to load approval config from: {}. No tools will be approved.", configFile, e);
        }
    }

    /**
     * Configuration file format for approved tools.
     */
    private record ApprovalConfig(List<ToolConfig> approvedTools) {
        public ApprovalConfig {
            if (approvedTools == null) {
                approvedTools = new ArrayList<>();
            }
        }
    }

    /**
     * Individual tool configuration.
     * Supports both simple string format and object format with rate limits.
     */
    private static class ToolConfig {
        private String name;
        private RateLimitYaml rateLimit;

        public ToolConfig() {
        }

        public ToolConfig(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public RateLimitYaml getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(RateLimitYaml rateLimit) {
            this.rateLimit = rateLimit;
        }
    }

    /**
     * Rate limit configuration from YAML.
     */
    private record RateLimitYaml(int requests, String window) {
    }
}

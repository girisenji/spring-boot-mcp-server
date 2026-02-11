package io.github.girisenji.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Auto MCP Server.
 * 
 * @param enabled   Whether the MCP server is enabled
 * @param endpoint  The endpoint path for MCP server
 * @param discovery Discovery configuration
 * @param tools     Tool configuration
 */
@ConfigurationProperties(prefix = "auto-mcp-server")
public record AutoMcpServerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("/mcp") String endpoint,
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue Discovery discovery,
        @DefaultValue Tools tools,
        @DefaultValue Performance performance) {

    /**
     * Discovery configuration for different API types.
     * 
     * @param openapiEnabled Whether to discover OpenAPI specs
     * @param restEnabled    Whether to discover REST endpoints
     * @param graphqlEnabled Whether to discover GraphQL endpoints
     */
    public record Discovery(
            @DefaultValue("true") boolean openapiEnabled,
            @DefaultValue("true") boolean restEnabled,
            @DefaultValue("true") boolean graphqlEnabled) {
        public Discovery() {
            this(true, true, true);
        }
    }

    /**
     * Tool configuration for filtering and customization.
     * 
     * @param includePatterns          Ant-style patterns for endpoints to include
     * @param excludePatterns          Ant-style patterns for endpoints to exclude
     * @param maxToolNameLength        Maximum length for tool names
     * @param useOperationIdAsToolName Use OpenAPI operationId as tool name if
     *                                 available
     * @param approvalConfigFile       Path to YAML file with approved tools
     *                                 (required)
     * @param duplicateToolStrategy    Strategy for handling duplicate tool names
     * @param logExcludedTools         Whether to log tools excluded by patterns
     */
    public record Tools(
            @DefaultValue("/**") String[] includePatterns,
            @DefaultValue({
                    "/actuator/**", "/error" }) String[] excludePatterns,
            @DefaultValue("100") int maxToolNameLength,
            @DefaultValue("true") boolean useOperationIdAsToolName,
            @DefaultValue("classpath:approved-tools.yml") String approvalConfigFile,
            @DefaultValue("FIRST_WINS") DuplicateToolStrategy duplicateToolStrategy,
            @DefaultValue("false") boolean logExcludedTools) {
        public Tools() {
            this(
                    new String[] { "/**" },
                    new String[] { "/actuator/**", "/error" },
                    100,
                    true,
                    "classpath:approved-tools.yml",
                    DuplicateToolStrategy.FIRST_WINS,
                    false);
        }

        public enum DuplicateToolStrategy {
            /**
             * Keep the first discovered tool, ignore duplicates.
             */
            FIRST_WINS,

            /**
             * Keep the last discovered tool, overwrite previous.
             */
            LAST_WINS,

            /**
             * Fail fast when duplicate tool name is detected.
             */
            FAIL_ON_DUPLICATE
        }
    }

    /**
     * Performance and resource management configuration.
     * 
     * @param eagerInit             Whether to initialize tool registry at startup
     * @param maxCachedTools        Maximum number of tools to cache in memory
     * @param cacheTtlSeconds       Time-to-live for cached tool metadata in seconds
     * @param maxConcurrentSessions Maximum concurrent SSE sessions allowed
     */
    public record Performance(
            @DefaultValue("false") boolean eagerInit,
            @DefaultValue("1000") int maxCachedTools,
            @DefaultValue("3600") int cacheTtlSeconds,
            @DefaultValue("100") int maxConcurrentSessions) {
        public Performance() {
            this(false, 1000, 3600, 100);
        }
    }

    public AutoMcpServerProperties() {
        this(true, "/mcp", "http://localhost:8080", new Discovery(), new Tools(), new Performance());
    }
}

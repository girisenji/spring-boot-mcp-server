package io.github.girisenji.ai.service;

import io.github.girisenji.ai.config.AutoMcpServerProperties;
import io.github.girisenji.ai.discovery.EndpointDiscoveryService;
import io.github.girisenji.ai.mcp.McpProtocol;
import io.github.girisenji.ai.model.RateLimitConfig;
import io.github.girisenji.ai.service.ToolConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpToolRegistryTest {

    @Mock
    private EndpointDiscoveryService discoveryService;

    private McpToolRegistry toolRegistry;
    private Set<String> approvedToolNames;
    private TestToolConfigurationService toolConfigService;
    private AutoMcpServerProperties properties;

    @BeforeEach
    void setUp() {
        approvedToolNames = new HashSet<>();
        toolConfigService = new TestToolConfigurationService(approvedToolNames);

        // Create real record instances instead of mocks (records can't be mocked in JDK
        // 21)
        AutoMcpServerProperties.Tools toolsConfig = new AutoMcpServerProperties.Tools(
                new String[] { "/**" },
                new String[] { "/actuator/**", "/error" },
                100,
                true,
                "classpath:approved-tools.yml",
                AutoMcpServerProperties.Tools.DuplicateToolStrategy.FIRST_WINS,
                false);

        properties = new AutoMcpServerProperties(
                true,
                "/mcp",
                "http://localhost:8080",
                new AutoMcpServerProperties.Discovery(),
                toolsConfig,
                new AutoMcpServerProperties.Performance(),
                new AutoMcpServerProperties.RateLimiting(),
                new AutoMcpServerProperties.Execution("PT30S", "PT5S", "10MB", "10MB"));

        toolRegistry = new McpToolRegistry(List.of(discoveryService), toolConfigService, properties);
    }

    @Test
    void testInitializeDiscoverTools() {
        // Given
        McpProtocol.Tool tool1 = new McpProtocol.Tool("test_tool_1", "Test tool 1", null);
        McpProtocol.Tool tool2 = new McpProtocol.Tool("test_tool_2", "Test tool 2", null);

        // Mark tools as approved
        approvedToolNames.add("test_tool_1");
        approvedToolNames.add("test_tool_2");

        when(discoveryService.isEnabled()).thenReturn(true);
        when(discoveryService.discoverTools()).thenReturn(List.of(tool1, tool2));
        when(discoveryService.getDiscoveryType()).thenReturn("Test");

        // When
        toolRegistry.initialize();

        // Then
        assertThat(toolRegistry.isInitialized()).isTrue();
        assertThat(toolRegistry.getToolCount()).isEqualTo(2);
        assertThat(toolRegistry.getAllTools()).containsExactlyInAnyOrder(tool1, tool2);
    }

    @Test
    void testGetToolByName() {
        // Given
        McpProtocol.Tool tool = new McpProtocol.Tool("test_tool", "Test tool", null);

        // Mark tool as approved
        approvedToolNames.add("test_tool");

        when(discoveryService.isEnabled()).thenReturn(true);
        when(discoveryService.discoverTools()).thenReturn(List.of(tool));
        when(discoveryService.getDiscoveryType()).thenReturn("Test");

        toolRegistry.initialize();

        // When
        var result = toolRegistry.getTool("test_tool");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(tool);
    }

    @Test
    void testHasTool() {
        // Given
        McpProtocol.Tool tool = new McpProtocol.Tool("test_tool", "Test tool", null);

        // Mark tool as approved
        approvedToolNames.add("test_tool");

        when(discoveryService.isEnabled()).thenReturn(true);
        when(discoveryService.discoverTools()).thenReturn(List.of(tool));
        when(discoveryService.getDiscoveryType()).thenReturn("Test");

        toolRegistry.initialize();

        // When & Then
        assertThat(toolRegistry.hasTool("test_tool")).isTrue();
        assertThat(toolRegistry.hasTool("non_existent")).isFalse();
    }

    @Test
    void testRefreshTools() {
        // Given
        McpProtocol.Tool tool1 = new McpProtocol.Tool("test_tool_1", "Test tool 1", null);
        McpProtocol.Tool tool2 = new McpProtocol.Tool("test_tool_2", "Test tool 2", null);

        // Initially approve only tool1
        approvedToolNames.add("test_tool_1");

        when(discoveryService.isEnabled()).thenReturn(true);
        when(discoveryService.discoverTools()).thenReturn(List.of(tool1), List.of(tool1, tool2));
        when(discoveryService.getDiscoveryType()).thenReturn("Test");

        toolRegistry.initialize();
        assertThat(toolRegistry.getToolCount()).isEqualTo(1);

        // When - simulate new tools discovered and approve tool2
        approvedToolNames.add("test_tool_2");
        toolRegistry.refreshTools();

        // Then
        assertThat(toolRegistry.getToolCount()).isEqualTo(2);
        assertThat(toolRegistry.getAllTools()).containsExactlyInAnyOrder(tool1, tool2);
    }

    @Test
    void testGetToolsWithPagination() {
        // Given
        McpProtocol.Tool tool1 = new McpProtocol.Tool("test_tool_1", "Test tool 1", null);
        McpProtocol.Tool tool2 = new McpProtocol.Tool("test_tool_2", "Test tool 2", null);
        McpProtocol.Tool tool3 = new McpProtocol.Tool("test_tool_3", "Test tool 3", null);

        // Approve all tools
        approvedToolNames.add("test_tool_1");
        approvedToolNames.add("test_tool_2");
        approvedToolNames.add("test_tool_3");

        when(discoveryService.isEnabled()).thenReturn(true);
        when(discoveryService.discoverTools()).thenReturn(List.of(tool1, tool2, tool3));
        when(discoveryService.getDiscoveryType()).thenReturn("Test");

        toolRegistry.initialize();

        // When
        List<McpProtocol.Tool> page1 = toolRegistry.getTools(0, 2);
        List<McpProtocol.Tool> page2 = toolRegistry.getTools(2, 2);

        // Then
        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);
    }

    @Test
    void testDisabledDiscoveryService() {
        // Given
        when(discoveryService.isEnabled()).thenReturn(false);

        // When
        toolRegistry.initialize();

        // Then
        assertThat(toolRegistry.getToolCount()).isZero();
        assertThat(toolRegistry.getAllTools()).isEmpty();
    }

    /**
     * Test stub for ToolConfigurationService that avoids Mockito issues with JDK
     * 21.
     */
    private static class TestToolConfigurationService extends ToolConfigurationService {
        private final Set<String> approvedToolNames;

        public TestToolConfigurationService(Set<String> approvedToolNames) {
            super(createMockProperties(), createMockResourceLoader(), createMockRateLimitService());
            this.approvedToolNames = approvedToolNames;
        }

        private static AutoMcpServerProperties createMockProperties() {
            // Create real record instances - records can't be mocked easily in JDK 21
            AutoMcpServerProperties.Tools tools = new AutoMcpServerProperties.Tools(
                    new String[] { "/**" }, // includePatterns
                    new String[] { "/actuator/**", "/error" }, // excludePatterns
                    100, // maxToolNameLength
                    true, // useOperationIdAsToolName
                    null, // approvalConfigFile - null to skip file loading
                    AutoMcpServerProperties.Tools.DuplicateToolStrategy.FIRST_WINS,
                    false // logExcludedTools
            );

            AutoMcpServerProperties.Discovery discovery = new AutoMcpServerProperties.Discovery();
            AutoMcpServerProperties.Performance performance = new AutoMcpServerProperties.Performance();
            AutoMcpServerProperties.RateLimiting rateLimiting = new AutoMcpServerProperties.RateLimiting();
            AutoMcpServerProperties.Execution execution = new AutoMcpServerProperties.Execution("PT30S", "PT5S", "10MB",
                    "10MB");

            return new AutoMcpServerProperties(
                    true, // enabled
                    "/mcp", // endpoint
                    "http://localhost:8080", // baseUrl
                    discovery,
                    tools,
                    performance,
                    rateLimiting,
                    execution);
        }

        private static org.springframework.core.io.ResourceLoader createMockResourceLoader() {
            return mock(org.springframework.core.io.ResourceLoader.class);
        }

        private static RateLimitService createMockRateLimitService() {
            // Create a real instance instead of mocking to avoid JDK 21 Mockito issues
            return new RateLimitService(100);
        }

        @Override
        public boolean isToolApproved(String toolName) {
            return approvedToolNames.contains(toolName);
        }

        @Override
        public Set<String> getApprovedToolNames() {
            return java.util.Collections.unmodifiableSet(approvedToolNames);
        }

        @Override
        public int getApprovedCount() {
            return approvedToolNames.size();
        }

        @Override
        public Optional<RateLimitConfig> getRateLimitConfig(String toolName) {
            return Optional.empty();
        }
    }
}

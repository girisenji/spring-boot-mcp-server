package com.girisenji.ai.service;

import com.girisenji.ai.config.AutoMcpServerProperties;
import com.girisenji.ai.discovery.EndpointDiscoveryService;
import com.girisenji.ai.mcp.McpProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpToolRegistryTest {

    @Mock
    private EndpointDiscoveryService discoveryService;

    @Mock
    private ToolApprovalService approvalService;

    @Mock
    private AutoMcpServerProperties properties;

    @Mock
    private AutoMcpServerProperties.Tools toolsConfig;

    private McpToolRegistry toolRegistry;
    private Set<String> approvedToolNames;

    @BeforeEach
    void setUp() {
        approvedToolNames = new HashSet<>();

        // Mock properties
        lenient().when(properties.tools()).thenReturn(toolsConfig);
        lenient().when(toolsConfig.duplicateToolStrategy())
                .thenReturn(AutoMcpServerProperties.Tools.DuplicateToolStrategy.FIRST_WINS);

        // Mock isToolApproved to check our tracking set
        lenient().when(approvalService.isToolApproved(anyString())).thenAnswer(invocation -> {
            String toolName = invocation.getArgument(0);
            return approvedToolNames.contains(toolName);
        });

        // Mock getApprovedToolNames to return our tracking set
        lenient().when(approvalService.getApprovedToolNames())
                .thenAnswer(invocation -> java.util.Collections.unmodifiableSet(approvedToolNames));

        // Mock getApprovedCount
        lenient().when(approvalService.getApprovedCount())
                .thenAnswer(invocation -> approvedToolNames.size());

        toolRegistry = new McpToolRegistry(List.of(discoveryService), approvalService, properties);
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
}

package io.github.girisenji.ai.controller;

import io.github.girisenji.ai.mcp.McpProtocol;
import io.github.girisenji.ai.service.AuditLogger;
import io.github.girisenji.ai.service.McpToolRegistry;
import io.github.girisenji.ai.service.ToolConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for {@link ToolManagementController}.
 */
@ExtendWith(MockitoExtension.class)
class ToolManagementControllerTest {

        @Mock
        private McpToolRegistry toolRegistry;

        @Mock
        private ToolConfigurationService toolConfigService;

        @Mock
        private AuditLogger auditLogger;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
                ToolManagementController controller = new ToolManagementController(
                                toolRegistry, toolConfigService, auditLogger, null);
                mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        }

        @Test
        void getSummary_shouldReturnDiscoveryStatistics() throws Exception {
                // Given
                List<McpProtocol.Tool> tools = List.of(
                                new McpProtocol.Tool("getUser", "Get a user", null),
                                new McpProtocol.Tool("listUsers", "List all users", null),
                                new McpProtocol.Tool("createUser", "Create a user", null));

                when(toolRegistry.getAllDiscoveredTools()).thenReturn(tools);
                when(toolConfigService.getApprovedCount()).thenReturn(2);
                when(toolConfigService.getApprovedToolNames()).thenReturn(Set.of("getUser", "listUsers"));

                // When & Then
                mockMvc.perform(get("/mcp/admin/tools/summary"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalDiscovered").value(3))
                                .andExpect(jsonPath("$.totalApproved").value(2))
                                .andExpect(jsonPath("$.unapproved").value(1))
                                .andExpect(jsonPath("$.approvedTools", hasSize(2)))
                                .andExpect(jsonPath("$.approvedTools", containsInAnyOrder("getUser", "listUsers")));
        }

        @Test
        void getDiscoveredTools_shouldReturnAllToolsWithApprovalStatus() throws Exception {
                // Given
                List<McpProtocol.Tool> tools = List.of(
                                new McpProtocol.Tool("getUser", "Get a user", null),
                                new McpProtocol.Tool("listUsers", "List all users", null));

                when(toolRegistry.getAllDiscoveredTools()).thenReturn(tools);
                when(toolConfigService.getApprovedCount()).thenReturn(1);
                when(toolConfigService.isToolApproved("getUser")).thenReturn(true);
                when(toolConfigService.isToolApproved("listUsers")).thenReturn(false);

                // When & Then
                mockMvc.perform(get("/mcp/admin/tools/discovered"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalDiscovered").value(2))
                                .andExpect(jsonPath("$.totalApproved").value(1))
                                .andExpect(jsonPath("$.tools", hasSize(2)))
                                .andExpect(jsonPath("$.tools[0].name").value("getUser"))
                                .andExpect(jsonPath("$.tools[0].description").value("Get a user"))
                                .andExpect(jsonPath("$.tools[0].approved").value(true))
                                .andExpect(jsonPath("$.tools[1].name").value("listUsers"))
                                .andExpect(jsonPath("$.tools[1].approved").value(false));
        }

        @Test
        void getDiscoveredToolsAsYaml_shouldGenerateYamlTemplate() throws Exception {
                // Given
                List<McpProtocol.Tool> tools = List.of(
                                new McpProtocol.Tool("getUser", "Get a user", null),
                                new McpProtocol.Tool("listUsers", "List all users", null));

                when(toolRegistry.getAllDiscoveredTools()).thenReturn(tools);
                when(toolConfigService.getApprovedCount()).thenReturn(1);
                when(toolConfigService.isToolApproved("getUser")).thenReturn(true);
                when(toolConfigService.isToolApproved("listUsers")).thenReturn(false);

                // When & Then
                mockMvc.perform(get("/mcp/admin/tools/yaml"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN_VALUE))
                                .andExpect(content().string(containsString("approvedTools:")))
                                .andExpect(content().string(containsString("# Get a user")))
                                .andExpect(content().string(containsString("- getUser")))
                                .andExpect(content().string(containsString("# - listUsers # Commented")))
                                .andExpect(content().string(containsString("# Total discovered: 2")))
                                .andExpect(content().string(containsString("# Currently approved: 1")));
        }

        @Test
        void getDiscoveredToolsAsYaml_withApprovedOnlyFlag_shouldOnlyReturnApprovedTools() throws Exception {
                // Given
                List<McpProtocol.Tool> tools = List.of(
                                new McpProtocol.Tool("getUser", "Get a user", null),
                                new McpProtocol.Tool("listUsers", "List all users", null));

                when(toolRegistry.getAllDiscoveredTools()).thenReturn(tools);
                when(toolConfigService.getApprovedCount()).thenReturn(1);
                when(toolConfigService.isToolApproved("getUser")).thenReturn(true);
                when(toolConfigService.isToolApproved("listUsers")).thenReturn(false);

                // When & Then
                mockMvc.perform(get("/mcp/admin/tools/yaml")
                                .param("approvedOnly", "true"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("- getUser")))
                                .andExpect(content().string(not(containsString("listUsers"))));
        }

        @Test
        void getDiscoveredToolsAsYaml_whenNoToolsDiscovered_shouldReturnEmptyYaml() throws Exception {
                // Given
                when(toolRegistry.getAllDiscoveredTools()).thenReturn(List.of());
                when(toolConfigService.getApprovedCount()).thenReturn(0);

                // When & Then
                mockMvc.perform(get("/mcp/admin/tools/yaml"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("approvedTools:")))
                                .andExpect(content().string(containsString("[] # No tools discovered")))
                                .andExpect(content().string(containsString("# Total discovered: 0")));
        }

        @Test
        void refreshDiscovery_shouldRefreshToolsAndLogAction() throws Exception {
                // Given
                List<McpProtocol.Tool> tools = List.of(
                                new McpProtocol.Tool("getUser", "Get a user", null));

                when(toolRegistry.getAllDiscoveredTools()).thenReturn(tools);
                when(toolConfigService.getApprovedCount()).thenReturn(1);
                when(toolConfigService.getApprovedToolNames()).thenReturn(Set.of("getUser"));

                // When & Then
                mockMvc.perform(post("/mcp/admin/tools/refresh"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Tool discovery refreshed"))
                                .andExpect(jsonPath("$.summary.totalDiscovered").value(1))
                                .andExpect(jsonPath("$.summary.totalApproved").value(1));

                // Verify audit log was called
                verify(auditLogger).logApprovalChange(
                                eq("system"),
                                anyString(),
                                eq(true),
                                eq("Tool discovery refresh triggered"));

                // Verify tools were refreshed
                verify(toolRegistry).refreshTools();
        }

        @Test
        void reloadConfiguration_shouldReloadApprovedToolsAndLogAction() throws Exception {
                // Given
                when(toolConfigService.getApprovedCount())
                                .thenReturn(2) // Before reload
                                .thenReturn(5) // After reload
                                .thenReturn(5); // For getSummary

                when(toolRegistry.getAllDiscoveredTools()).thenReturn(
                                List.of(
                                                new McpProtocol.Tool("tool1", "Tool 1", null),
                                                new McpProtocol.Tool("tool2", "Tool 2", null),
                                                new McpProtocol.Tool("tool3", "Tool 3", null),
                                                new McpProtocol.Tool("tool4", "Tool 4", null),
                                                new McpProtocol.Tool("tool5", "Tool 5", null)));

                when(toolConfigService.getApprovedToolNames()).thenReturn(
                                Set.of("tool1", "tool2", "tool3", "tool4", "tool5"));

                // When & Then
                mockMvc.perform(post("/mcp/admin/tools/reload"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message")
                                                .value("Approved tools configuration reloaded successfully"))
                                .andExpect(jsonPath("$.previousCount").value(2))
                                .andExpect(jsonPath("$.currentCount").value(5))
                                .andExpect(jsonPath("$.summary.totalDiscovered").value(5))
                                .andExpect(jsonPath("$.summary.totalApproved").value(5));

                // Verify reload was called
                verify(toolConfigService).reloadApprovedTools();

                // Verify audit log was called with correct message
                verify(auditLogger).logApprovalChange(
                                eq("approved-tools.yml"),
                                anyString(),
                                eq(true),
                                eq("Configuration reloaded: 2 → 5 approved tools"));
        }

        @Test
        void reloadConfiguration_whenCountDecreases_shouldLogCorrectly() throws Exception {
                // Given
                when(toolConfigService.getApprovedCount())
                                .thenReturn(10) // Before reload
                                .thenReturn(3) // After reload
                                .thenReturn(3); // For getSummary

                when(toolRegistry.getAllDiscoveredTools()).thenReturn(
                                List.of(
                                                new McpProtocol.Tool("tool1", "Tool 1", null),
                                                new McpProtocol.Tool("tool2", "Tool 2", null),
                                                new McpProtocol.Tool("tool3", "Tool 3", null)));

                when(toolConfigService.getApprovedToolNames()).thenReturn(
                                Set.of("tool1", "tool2", "tool3"));

                // When & Then
                mockMvc.perform(post("/mcp/admin/tools/reload"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.previousCount").value(10))
                                .andExpect(jsonPath("$.currentCount").value(3));

                // Verify audit log shows count decrease
                verify(auditLogger).logApprovalChange(
                                eq("approved-tools.yml"),
                                anyString(),
                                eq(true),
                                eq("Configuration reloaded: 10 → 3 approved tools"));
        }

        @Test
        void reloadConfiguration_whenNoChange_shouldStillReload() throws Exception {
                // Given
                when(toolConfigService.getApprovedCount()).thenReturn(2);
                when(toolRegistry.getAllDiscoveredTools()).thenReturn(
                                List.of(
                                                new McpProtocol.Tool("tool1", "Tool 1", null),
                                                new McpProtocol.Tool("tool2", "Tool 2", null)));

                when(toolConfigService.getApprovedToolNames()).thenReturn(Set.of("tool1", "tool2"));

                // When & Then
                mockMvc.perform(post("/mcp/admin/tools/reload"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.previousCount").value(2))
                                .andExpect(jsonPath("$.currentCount").value(2));

                // Verify reload was still called
                verify(toolConfigService).reloadApprovedTools();

                // Verify audit log shows no change
                verify(auditLogger).logApprovalChange(
                                eq("approved-tools.yml"),
                                anyString(),
                                eq(true),
                                eq("Configuration reloaded: 2 → 2 approved tools"));
        }
}

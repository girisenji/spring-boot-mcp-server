package io.github.girisenji.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testRequestCreation() {
        McpProtocol.Request request = new McpProtocol.Request("2.0", "1", "initialize", Map.of("key", "value"));

        assertThat(request.jsonrpc()).isEqualTo("2.0");
        assertThat(request.id()).isEqualTo("1");
        assertThat(request.method()).isEqualTo("initialize");
        assertThat(request.params()).containsEntry("key", "value");
    }

    @Test
    void testRequestDefaultJsonRpc() {
        McpProtocol.Request request = new McpProtocol.Request(null, "1", "test", null);
        assertThat(request.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void testResponseCreation() {
        McpProtocol.Response response = new McpProtocol.Response("2.0", "1", Map.of("status", "ok"), null);

        assertThat(response.jsonrpc()).isEqualTo("2.0");
        assertThat(response.id()).isEqualTo("1");
        assertThat(response.result()).isEqualTo(Map.of("status", "ok"));
        assertThat(response.error()).isNull();
    }

    @Test
    void testResponseDefaultJsonRpc() {
        McpProtocol.Response response = new McpProtocol.Response(null, "1", "result", null);
        assertThat(response.jsonrpc()).isEqualTo("2.0");
    }

    @Test
    void testResponseWithError() {
        McpProtocol.Error error = new McpProtocol.Error(-32600, "Invalid Request", null);
        McpProtocol.Response response = new McpProtocol.Response("2.0", "1", null, error);

        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo(-32600);
        assertThat(response.error().message()).isEqualTo("Invalid Request");
    }

    @Test
    void testNotification() {
        Map<String, Object> params = Map.of("step", 1);
        McpProtocol.Notification notification = new McpProtocol.Notification("2.0", "progress", params);

        assertThat(notification.jsonrpc()).isEqualTo("2.0");
        assertThat(notification.method()).isEqualTo("progress");
        assertThat(notification.params()).isEqualTo(params);
    }

    @Test
    void testErrorRecord() {
        Map<String, Object> errorData = Map.of("line", 10);
        McpProtocol.Error error = new McpProtocol.Error(-32700, "Parse error", errorData);

        assertThat(error.code()).isEqualTo(-32700);
        assertThat(error.message()).isEqualTo("Parse error");
        assertThat(error.data()).isEqualTo(errorData);
    }

    @Test
    void testContentText() {
        McpProtocol.Content content = McpProtocol.Content.text("Hello, world!");

        assertThat(content.type()).isEqualTo("text");
        assertThat(content.text()).isEqualTo("Hello, world!");
        assertThat(content.data()).isNull();
    }

    @Test
    void testContentData() throws Exception {
        JsonNode jsonData = objectMapper.createObjectNode().put("key", "value");
        McpProtocol.Content content = McpProtocol.Content.data(jsonData);

        assertThat(content.type()).isEqualTo("data");
        assertThat(content.data()).isNotNull();
        assertThat(content.text()).isNull();
    }

    @Test
    void testToolRecord() {
        JsonNode schema = objectMapper.createObjectNode()
                .put("type", "object");

        McpProtocol.Tool tool = new McpProtocol.Tool("getUserById", "Get user by ID", schema);

        assertThat(tool.name()).isEqualTo("getUserById");
        assertThat(tool.description()).isEqualTo("Get user by ID");
        assertThat(tool.inputSchema()).isNotNull();
    }

    @Test
    void testInitializeResult() {
        McpProtocol.ServerInfo serverInfo = new McpProtocol.ServerInfo("TestServer", "1.0.0");
        McpProtocol.ToolsCapability toolsCapability = new McpProtocol.ToolsCapability();
        McpProtocol.ResourcesCapability resourcesCapability = new McpProtocol.ResourcesCapability(false, false);
        McpProtocol.PromptsCapability promptsCapability = new McpProtocol.PromptsCapability(false);
        McpProtocol.ServerCapabilities capabilities = new McpProtocol.ServerCapabilities(toolsCapability,
                resourcesCapability, promptsCapability);

        McpProtocol.InitializeResult result = new McpProtocol.InitializeResult("2024-11-05", capabilities, serverInfo);

        assertThat(result.protocolVersion()).isEqualTo("2024-11-05");
        assertThat(result.serverInfo().name()).isEqualTo("TestServer");
        assertThat(result.serverInfo().version()).isEqualTo("1.0.0");
        assertThat(result.capabilities().tools()).isNotNull();
    }

    @Test
    void testListToolsResult() {
        JsonNode emptySchema = objectMapper.createObjectNode();
        McpProtocol.Tool tool1 = new McpProtocol.Tool("tool1", "First tool", emptySchema);
        McpProtocol.Tool tool2 = new McpProtocol.Tool("tool2", "Second tool", emptySchema);

        McpProtocol.ListToolsResult result = new McpProtocol.ListToolsResult(List.of(tool1, tool2), "cursor123");

        assertThat(result.tools()).hasSize(2);
        assertThat(result.tools().get(0).name()).isEqualTo("tool1");
        assertThat(result.tools().get(1).name()).isEqualTo("tool2");
        assertThat(result.nextCursor()).isEqualTo("cursor123");
    }

    @Test
    void testListToolsResultNoCursor() {
        McpProtocol.ListToolsResult result = new McpProtocol.ListToolsResult(List.of(), null);

        assertThat(result.tools()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void testCallToolResult() {
        McpProtocol.Content content1 = McpProtocol.Content.text("Result text");
        McpProtocol.Content content2 = McpProtocol.Content.text("Additional info");

        McpProtocol.CallToolResult result = new McpProtocol.CallToolResult(List.of(content1, content2), false);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).text()).isEqualTo("Result text");
        assertThat(result.content().get(1).text()).isEqualTo("Additional info");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void testCallToolResultError() {
        McpProtocol.Content errorContent = McpProtocol.Content.text("Error: Something went wrong");
        McpProtocol.CallToolResult result = new McpProtocol.CallToolResult(List.of(errorContent), true);

        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).text()).contains("Error");
    }

    @Test
    void testErrorCodes() {
        assertThat(McpProtocol.PARSE_ERROR).isEqualTo(-32700);
        assertThat(McpProtocol.INVALID_REQUEST).isEqualTo(-32600);
        assertThat(McpProtocol.METHOD_NOT_FOUND).isEqualTo(-32601);
        assertThat(McpProtocol.INVALID_PARAMS).isEqualTo(-32602);
        assertThat(McpProtocol.INTERNAL_ERROR).isEqualTo(-32603);
    }

    @Test
    void testServerInfo() {
        McpProtocol.ServerInfo info = new McpProtocol.ServerInfo("MyServer", "2.0.0");
        assertThat(info.name()).isEqualTo("MyServer");
        assertThat(info.version()).isEqualTo("2.0.0");
    }

    @Test
    void testServerCapabilities() {
        McpProtocol.ToolsCapability toolsCapability = new McpProtocol.ToolsCapability();
        McpProtocol.ResourcesCapability resourcesCapability = new McpProtocol.ResourcesCapability(false, false);
        McpProtocol.PromptsCapability promptsCapability = new McpProtocol.PromptsCapability(false);
        McpProtocol.ServerCapabilities capabilities = new McpProtocol.ServerCapabilities(toolsCapability,
                resourcesCapability, promptsCapability);
        assertThat(capabilities.tools()).isNotNull();
        assertThat(capabilities.resources()).isNotNull();
        assertThat(capabilities.prompts()).isNotNull();
    }
}

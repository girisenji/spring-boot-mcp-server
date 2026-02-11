package com.girisenji.ai.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * MCP Protocol models following the Model Context Protocol specification.
 */
public class McpProtocol {

    /**
     * Base MCP message.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public sealed interface Message permits Request, Response, Notification {}

    /**
     * MCP Request message.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
        String jsonrpc,
        String id,
        String method,
        Map<String, Object> params
    ) implements Message {
        public Request {
            if (jsonrpc == null) jsonrpc = "2.0";
        }
    }

    /**
     * MCP Response message.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
        String jsonrpc,
        String id,
        Object result,
        Error error
    ) implements Message {
        public Response {
            if (jsonrpc == null) jsonrpc = "2.0";
        }
    }

    /**
     * MCP Notification message.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Notification(
        String jsonrpc,
        String method,
        Map<String, Object> params
    ) implements Message {
        public Notification {
            if (jsonrpc == null) jsonrpc = "2.0";
        }
    }

    /**
     * MCP Error.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(
        int code,
        String message,
        Object data
    ) {}

    /**
     * Tool definition.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
        String name,
        String description,
        JsonNode inputSchema
    ) {}

    /**
     * List tools result.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ListToolsResult(
        List<Tool> tools,
        String nextCursor
    ) {}

    /**
     * Call tool result.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CallToolResult(
        List<Content> content,
        boolean isError
    ) {
        public CallToolResult(List<Content> content) {
            this(content, false);
        }
    }

    /**
     * Content item in tool result.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(
        String type,
        String text,
        Object data
    ) {
        public static Content text(String text) {
            return new Content("text", text, null);
        }
        
        public static Content data(Object data) {
            return new Content("data", null, data);
        }
    }

    /**
     * Server capabilities.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServerCapabilities(
        ToolsCapability tools
    ) {}

    /**
     * Tools capability.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolsCapability(
        boolean listChanged
    ) {
        public ToolsCapability() {
            this(true);
        }
    }

    /**
     * Server info.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServerInfo(
        String name,
        String version
    ) {}

    /**
     * Initialize result.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InitializeResult(
        String protocolVersion,
        ServerCapabilities capabilities,
        ServerInfo serverInfo
    ) {}

    // Error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
}

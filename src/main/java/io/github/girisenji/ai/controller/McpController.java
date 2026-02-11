package io.github.girisenji.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.girisenji.ai.mcp.McpProtocol;
import io.github.girisenji.ai.service.McpToolExecutor;
import io.github.girisenji.ai.service.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * Controller that exposes the MCP protocol endpoint using Server-Sent Events
 * (SSE).
 */
@RestController
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpToolRegistry toolRegistry;
    private final McpToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final String mcpEndpoint;

    public McpController(
            McpToolRegistry toolRegistry,
            McpToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            String mcpEndpoint) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.mcpEndpoint = mcpEndpoint;
    }

    /**
     * Main MCP endpoint using Server-Sent Events.
     */
    @PostMapping(value = "${auto-mcp-server.endpoint:/mcp}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> handleMcpSession(@RequestBody(required = false) String initialMessage) {
        log.info("New MCP session started");

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();

        return sink.asFlux()
                .doOnSubscribe(subscription -> {
                    log.debug("Client subscribed to MCP stream");
                    // Send endpoint message
                    sendEndpointMessage(sink);
                })
                .doOnCancel(() -> log.info("MCP session cancelled"))
                .doOnComplete(() -> log.info("MCP session completed"))
                .doOnError(error -> log.error("MCP session error", error));
    }

    /**
     * Alternative POST endpoint for JSON-RPC style requests.
     */
    @PostMapping(value = "${auto-mcp-server.endpoint:/mcp}/messages", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public McpProtocol.Response handleMessage(@RequestBody McpProtocol.Request request) {
        log.info("Received MCP request: {}", request.method());

        try {
            Object result = handleMethod(request.method(), request.params());
            return new McpProtocol.Response("2.0", request.id(), result, null);
        } catch (Exception e) {
            log.error("Error handling MCP request", e);
            return new McpProtocol.Response(
                    "2.0",
                    request.id(),
                    null,
                    new McpProtocol.Error(
                            McpProtocol.INTERNAL_ERROR,
                            e.getMessage(),
                            null));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("${auto-mcp-server.endpoint:/mcp}/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "toolCount", toolRegistry.getToolCount(),
                "protocolVersion", PROTOCOL_VERSION);
    }

    private void sendEndpointMessage(Sinks.Many<ServerSentEvent<String>> sink) {
        try {
            Map<String, String> endpoint = Map.of("endpoint", mcpEndpoint);
            String json = objectMapper.writeValueAsString(endpoint);
            sink.tryEmitNext(ServerSentEvent.builder(json).event("endpoint").build());
        } catch (Exception e) {
            log.error("Failed to send endpoint message", e);
        }
    }

    private Object handleMethod(String method, Map<String, Object> params) throws Exception {
        return switch (method) {
            case "initialize" -> handleInitialize(params);
            case "tools/list" -> handleListTools(params);
            case "tools/call" -> handleCallTool(params);
            case "ping" -> Map.of("status", "pong");
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };
    }

    private McpProtocol.InitializeResult handleInitialize(Map<String, Object> params) {
        log.info("Initializing MCP session");

        McpProtocol.ServerInfo serverInfo = new McpProtocol.ServerInfo(
                "Auto MCP Server",
                "1.0.0");

        McpProtocol.ServerCapabilities capabilities = new McpProtocol.ServerCapabilities(
                new McpProtocol.ToolsCapability());

        return new McpProtocol.InitializeResult(
                PROTOCOL_VERSION,
                capabilities,
                serverInfo);
    }

    private McpProtocol.ListToolsResult handleListTools(Map<String, Object> params) {
        log.info("Listing tools");

        String cursor = params != null ? (String) params.get("cursor") : null;
        int offset = 0;
        int limit = 100;

        if (cursor != null) {
            try {
                offset = Integer.parseInt(cursor);
            } catch (NumberFormatException e) {
                log.warn("Invalid cursor value: {}", cursor);
            }
        }

        List<McpProtocol.Tool> tools = toolRegistry.getTools(offset, limit);
        String nextCursor = (offset + tools.size() < toolRegistry.getToolCount())
                ? String.valueOf(offset + limit)
                : null;

        return new McpProtocol.ListToolsResult(tools, nextCursor);
    }

    private McpProtocol.CallToolResult handleCallTool(Map<String, Object> params) {
        if (params == null) {
            throw new IllegalArgumentException("Missing parameters for tool call");
        }

        String toolName = (String) params.get("name");
        if (toolName == null) {
            throw new IllegalArgumentException("Missing tool name");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        log.info("Calling tool: {} with arguments: {}", toolName, arguments);

        if (!toolRegistry.hasTool(toolName)) {
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: Tool not found: " + toolName)),
                    true);
        }

        return toolExecutor.executeTool(toolName, arguments);
    }
}

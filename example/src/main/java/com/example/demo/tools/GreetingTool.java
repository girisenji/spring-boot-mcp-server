package com.example.demo.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.girisenji.ai.model.McpTool;
import io.github.girisenji.ai.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Example custom MCP tool that generates personalized greetings.
 * Demonstrates direct Java execution without HTTP endpoints.
 */
@Component
public class GreetingTool implements McpTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "greet";
    }

    @Override
    public String getDescription() {
        return "Generate a personalized greeting message";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode nameProperty = objectMapper.createObjectNode();
        nameProperty.put("type", "string");
        nameProperty.put("description", "Name of the person to greet");
        properties.set("name", nameProperty);

        ObjectNode styleProperty = objectMapper.createObjectNode();
        styleProperty.put("type", "string");
        styleProperty.put("description", "Greeting style: formal, casual, or friendly");
        styleProperty.put("enum", objectMapper.createArrayNode().add("formal").add("casual").add("friendly"));
        styleProperty.put("default", "friendly");
        properties.set("style", styleProperty);

        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode().add("name"));

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String name = (String) arguments.getOrDefault("name", "there");
        String style = (String) arguments.getOrDefault("style", "friendly");

        String greeting = switch (style.toLowerCase()) {
            case "formal" -> String.format("Good day, %s. It is a pleasure to make your acquaintance.", name);
            case "casual" -> String.format("Hey %s! What's up?", name);
            case "friendly" -> String.format("Hello %s! How are you doing today?", name);
            default -> String.format("Hello, %s!", name);
        };

        return ToolResult.success(greeting);
    }
}

package com.example.demo.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.girisenji.ai.model.McpTool;
import io.github.girisenji.ai.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Example custom MCP tool that performs mathematical calculations.
 * Demonstrates error handling and validation.
 */
@Component
public class CalculatorTool implements McpTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "calculate";
    }

    @Override
    public String getDescription() {
        return "Perform basic mathematical calculations (add, subtract, multiply, divide)";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode operationProperty = objectMapper.createObjectNode();
        operationProperty.put("type", "string");
        operationProperty.put("description", "Mathematical operation to perform");
        operationProperty.put("enum", objectMapper.createArrayNode()
                .add("add").add("subtract").add("multiply").add("divide"));
        properties.set("operation", operationProperty);

        ObjectNode aProperty = objectMapper.createObjectNode();
        aProperty.put("type", "number");
        aProperty.put("description", "First number");
        properties.set("a", aProperty);

        ObjectNode bProperty = objectMapper.createObjectNode();
        bProperty.put("type", "number");
        bProperty.put("description", "Second number");
        properties.set("b", bProperty);

        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode()
                .add("operation").add("a").add("b"));

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        try {
            String operation = (String) arguments.get("operation");
            double a = getNumber(arguments, "a");
            double b = getNumber(arguments, "b");

            double result = switch (operation.toLowerCase()) {
                case "add" -> a + b;
                case "subtract" -> a - b;
                case "multiply" -> a * b;
                case "divide" -> {
                    if (b == 0) {
                        return ToolResult.error("Cannot divide by zero");
                    }
                    yield a / b;
                }
                default -> {
                    return ToolResult.error("Unknown operation: " + operation);
                }
            };

            String message = String.format("%s %s %s = %s", a, operation, b, result);
            return ToolResult.success(message);

        } catch (Exception e) {
            return ToolResult.error(e);
        }
    }

    private double getNumber(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}

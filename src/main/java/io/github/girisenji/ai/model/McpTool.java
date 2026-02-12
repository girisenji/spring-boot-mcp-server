package io.github.girisenji.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Interface for creating custom MCP tools with direct Java execution.
 * Implement this interface and annotate with @Component to create executable
 * tools.
 * 
 * <p>
 * Example:
 * </p>
 * 
 * <pre>
 * &#64;Component
 * public class GreetingTool implements McpTool {
 *     &#64;Override
 *     public String getName() {
 *         return "greet";
 *     }
 *     
 *     &#64;Override
 *     public String getDescription() {
 *         return "Generate a personalized greeting";
 *     }
 *     
 *     &#64;Override
 *     public JsonNode getInputSchema() {
 *         // Define JSON Schema for parameters
 *         return JsonSchemaGenerator.createSchema(...);
 *     }
 *     
 *     &#64;Override
 *     public ToolResult execute(Map<String, Object> arguments) {
 *         String name = (String) arguments.get("name");
 *         return ToolResult.success("Hello, " + name + "!");
 *     }
 * }
 * </pre>
 */
public interface McpTool {

    /**
     * Unique identifier for the tool.
     * Should be lowercase with underscores (e.g., "read_file", "search_database").
     * 
     * @return the tool name
     */
    String getName();

    /**
     * Human-readable description of what the tool does.
     * This helps AI agents understand when to use the tool.
     * 
     * @return the tool description
     */
    String getDescription();

    /**
     * JSON Schema defining the tool's input parameters.
     * Describes the expected structure, types, and constraints of arguments.
     * 
     * @return JSON Schema as JsonNode
     */
    JsonNode getInputSchema();

    /**
     * Execute the tool with the given arguments.
     * This is called when an AI agent invokes the tool.
     * 
     * @param arguments map of parameter name to value
     * @return ToolResult containing the execution result or error
     */
    ToolResult execute(Map<String, Object> arguments);
}

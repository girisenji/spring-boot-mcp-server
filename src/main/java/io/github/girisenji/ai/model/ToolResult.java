package io.github.girisenji.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a tool execution containing content and error status.
 * Provides factory methods for creating success and error results.
 */
public record ToolResult(
        List<ToolContent> content,
        boolean isError) {

    /**
     * Create a successful tool result with text content.
     * 
     * @param text the result text
     * @return ToolResult with success status
     */
    public static ToolResult success(String text) {
        return new ToolResult(
                List.of(new ToolContent(ToolContent.Type.TEXT, text, null)),
                false);
    }

    /**
     * Create a successful tool result with structured data.
     * 
     * @param data JSON data
     * @return ToolResult with success status
     */
    public static ToolResult success(JsonNode data) {
        return new ToolResult(
                List.of(new ToolContent(ToolContent.Type.DATA, null, data)),
                false);
    }

    /**
     * Create a successful tool result with mixed content.
     * 
     * @param content list of content items
     * @return ToolResult with success status
     */
    public static ToolResult success(List<ToolContent> content) {
        return new ToolResult(content, false);
    }

    /**
     * Create an error tool result.
     * 
     * @param errorMessage the error message
     * @return ToolResult with error status
     */
    public static ToolResult error(String errorMessage) {
        return new ToolResult(
                List.of(new ToolContent(ToolContent.Type.TEXT, errorMessage, null)),
                true);
    }

    /**
     * Create an error tool result from an exception.
     * 
     * @param throwable the exception
     * @return ToolResult with error status and exception details
     */
    public static ToolResult error(Throwable throwable) {
        String message = throwable.getMessage() != null
                ? throwable.getMessage()
                : throwable.getClass().getSimpleName();
        return error("Error: " + message);
    }

    /**
     * Represents a single piece of content in a tool result.
     */
    public record ToolContent(
            Type type,
            String text,
            JsonNode data) {

        public enum Type {
            TEXT,
            DATA
        }

        public static ToolContent text(String text) {
            return new ToolContent(Type.TEXT, text, null);
        }

        public static ToolContent data(JsonNode data) {
            return new ToolContent(Type.DATA, null, data);
        }
    }
}

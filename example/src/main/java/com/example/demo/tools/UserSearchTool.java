package com.example.demo.tools;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.girisenji.ai.model.McpTool;
import io.github.girisenji.ai.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Example custom MCP tool that searches users by name.
 * Demonstrates integration with Spring services and returning structured data.
 */
@Component
public class UserSearchTool implements McpTool {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    public UserSearchTool(UserService userService) {
        this.userService = userService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "search_users";
    }

    @Override
    public String getDescription() {
        return "Search for users by name (case-insensitive partial match)";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode queryProperty = objectMapper.createObjectNode();
        queryProperty.put("type", "string");
        queryProperty.put("description", "Search query for user name");
        properties.set("query", queryProperty);

        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode().add("query"));

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        try {
            String query = (String) arguments.get("query");
            if (query == null || query.trim().isEmpty()) {
                return ToolResult.error("Search query cannot be empty");
            }

            List<User> allUsers = userService.findAll();
            List<User> matchingUsers = allUsers.stream()
                    .filter(user -> user.name().toLowerCase().contains(query.toLowerCase()))
                    .toList();

            if (matchingUsers.isEmpty()) {
                return ToolResult.success("No users found matching: " + query);
            }

            // Convert to JSON
            ArrayNode usersArray = objectMapper.createArrayNode();
            for (User user : matchingUsers) {
                ObjectNode userNode = objectMapper.createObjectNode();
                userNode.put("id", user.id());
                userNode.put("name", user.name());
                userNode.put("email", user.email());
                userNode.put("age", user.age());
                usersArray.add(userNode);
            }

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("count", matchingUsers.size());
            resultNode.set("users", usersArray);

            return ToolResult.success(resultNode);

        } catch (Exception e) {
            return ToolResult.error(e);
        }
    }
}

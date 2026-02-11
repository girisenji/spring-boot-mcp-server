package com.girisenji.ai.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisenji.ai.config.AutoMcpServerProperties;
import com.girisenji.ai.mcp.McpProtocol;
import graphql.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Discovers GraphQL queries and mutations and converts them to MCP tools.
 */
public class GraphQLDiscoveryService implements EndpointDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(GraphQLDiscoveryService.class);

    private final AutoMcpServerProperties properties;
    private final ObjectMapper objectMapper;
    private final GraphQLSchema graphQLSchema;

    public GraphQLDiscoveryService(
            AutoMcpServerProperties properties,
            ObjectMapper objectMapper,
            GraphQLSchema graphQLSchema) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.graphQLSchema = graphQLSchema;
    }

    @Override
    public List<McpProtocol.Tool> discoverTools() {
        if (graphQLSchema == null) {
            log.debug("No GraphQL schema found");
            return Collections.emptyList();
        }

        List<McpProtocol.Tool> tools = new ArrayList<>();

        // Discover queries
        GraphQLObjectType queryType = graphQLSchema.getQueryType();
        if (queryType != null) {
            tools.addAll(discoverFieldsAsTools(queryType, "query"));
        }

        // Discover mutations
        GraphQLObjectType mutationType = graphQLSchema.getMutationType();
        if (mutationType != null) {
            tools.addAll(discoverFieldsAsTools(mutationType, "mutation"));
        }

        log.info("Discovered {} tools from GraphQL schema", tools.size());
        return tools;
    }

    @Override
    public String getDiscoveryType() {
        return "GraphQL";
    }

    @Override
    public boolean isEnabled() {
        return properties.discovery().graphqlEnabled() && graphQLSchema != null;
    }

    private List<McpProtocol.Tool> discoverFieldsAsTools(GraphQLObjectType type, String prefix) {
        List<McpProtocol.Tool> tools = new ArrayList<>();

        for (GraphQLFieldDefinition field : type.getFieldDefinitions()) {
            McpProtocol.Tool tool = convertFieldToTool(field, prefix);
            if (tool != null) {
                tools.add(tool);
            }
        }

        return tools;
    }

    private McpProtocol.Tool convertFieldToTool(GraphQLFieldDefinition field, String prefix) {
        try {
            String toolName = prefix + "_" + field.getName();
            String description = generateDescription(field, prefix);
            JsonNode inputSchema = generateInputSchema(field);

            return new McpProtocol.Tool(toolName, description, inputSchema);
        } catch (Exception e) {
            log.warn("Failed to convert GraphQL field to tool: {}", field.getName(), e);
            return null;
        }
    }

    private String generateDescription(GraphQLFieldDefinition field, String prefix) {
        StringBuilder desc = new StringBuilder();

        desc.append("GraphQL ").append(prefix).append(": ").append(field.getName());

        if (StringUtils.hasText(field.getDescription())) {
            desc.append(" - ").append(field.getDescription());
        }

        return desc.toString();
    }

    private JsonNode generateInputSchema(GraphQLFieldDefinition field) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        List<String> required = new ArrayList<>();

        for (GraphQLArgument argument : field.getArguments()) {
            JsonNode argSchema = convertGraphQLTypeToJsonSchema(argument.getType());
            properties.set(argument.getName(), argSchema);

            if (argument.getType() instanceof GraphQLNonNull) {
                required.add(argument.getName());
            }

            // Add description if available
            if (StringUtils.hasText(argument.getDescription())) {
                ((ObjectNode) argSchema).put("description", argument.getDescription());
            }
        }

        schema.set("properties", properties);

        if (!required.isEmpty()) {
            var requiredArray = objectMapper.createArrayNode();
            required.forEach(requiredArray::add);
            schema.set("required", requiredArray);
        }

        return schema;
    }

    private JsonNode convertGraphQLTypeToJsonSchema(GraphQLType type) {
        ObjectNode schema = objectMapper.createObjectNode();

        // Unwrap non-null and list wrappers
        GraphQLType unwrapped = type;
        boolean isList = false;

        if (unwrapped instanceof GraphQLNonNull) {
            unwrapped = ((GraphQLNonNull) unwrapped).getWrappedType();
        }

        if (unwrapped instanceof GraphQLList) {
            isList = true;
            unwrapped = ((GraphQLList) unwrapped).getWrappedType();
            if (unwrapped instanceof GraphQLNonNull) {
                unwrapped = ((GraphQLNonNull) unwrapped).getWrappedType();
            }
        }

        // Get the named type
        if (unwrapped instanceof GraphQLNamedType namedType) {
            String typeName = namedType.getName();
            String jsonType = mapGraphQLTypeToJsonType(typeName);

            if (isList) {
                schema.put("type", "array");
                ObjectNode items = objectMapper.createObjectNode();
                items.put("type", jsonType);
                schema.set("items", items);
            } else {
                schema.put("type", jsonType);
            }
        } else {
            schema.put("type", "string");
        }

        return schema;
    }

    private String mapGraphQLTypeToJsonType(String graphQLType) {
        return switch (graphQLType) {
            case "Int", "Float" -> "number";
            case "String", "ID" -> "string";
            case "Boolean" -> "boolean";
            default -> "object";
        };
    }
}

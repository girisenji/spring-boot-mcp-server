package com.girisenji.ai.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for generating JSON schemas from Java types.
 */
public class JsonSchemaGenerator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaGenerator.class);
    private final SchemaGenerator generator;
    private final ObjectMapper objectMapper;

    public JsonSchemaGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12,
            OptionPreset.PLAIN_JSON
        );
        SchemaGeneratorConfig config = configBuilder.build();
        this.generator = new SchemaGenerator(config);
    }

    /**
     * Generate JSON schema for a Java type.
     */
    public JsonNode generateSchema(Class<?> type) {
        try {
            return generator.generateSchema(type);
        } catch (Exception e) {
            log.warn("Failed to generate schema for type: {}", type.getName(), e);
            return createDefaultObjectSchema();
        }
    }

    /**
     * Generate JSON schema for method parameters.
     */
    public JsonNode generateSchemaForMethod(Method method) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            MethodParameter methodParam = new MethodParameter(method, i);
            
            String paramName = getParameterName(param, methodParam);
            if (paramName != null) {
                JsonNode paramSchema = generateParameterSchema(param);
                properties.set(paramName, paramSchema);
            }
        }
        
        schema.set("properties", properties);
        return schema;
    }

    /**
     * Generate schema for a method parameter.
     */
    private JsonNode generateParameterSchema(Parameter param) {
        Class<?> paramType = param.getType();
        
        // For primitive types and common types
        if (isPrimitiveOrWrapper(paramType) || paramType == String.class) {
            return createSimpleSchema(paramType);
        }
        
        // For complex types, generate full schema
        return generateSchema(paramType);
    }

    /**
     * Get parameter name from annotations or reflection.
     */
    private String getParameterName(Parameter param, MethodParameter methodParam) {
        // Check @RequestParam
        RequestParam requestParam = param.getAnnotation(RequestParam.class);
        if (requestParam != null) {
            return requestParam.value().isEmpty() ? requestParam.name() : requestParam.value();
        }
        
        // Check @PathVariable
        PathVariable pathVariable = param.getAnnotation(PathVariable.class);
        if (pathVariable != null) {
            return pathVariable.value().isEmpty() ? pathVariable.name() : pathVariable.value();
        }
        
        // Check @RequestBody
        RequestBody requestBody = param.getAnnotation(RequestBody.class);
        if (requestBody != null) {
            return "body";
        }
        
        // Fallback to parameter name from reflection
        if (param.isNamePresent()) {
            return param.getName();
        }
        
        return null;
    }

    /**
     * Create a simple JSON schema for primitive types.
     */
    private JsonNode createSimpleSchema(Class<?> type) {
        ObjectNode schema = objectMapper.createObjectNode();
        
        if (type == String.class || type == char.class || type == Character.class) {
            schema.put("type", "string");
        } else if (type == int.class || type == Integer.class || 
                   type == long.class || type == Long.class ||
                   type == short.class || type == Short.class ||
                   type == byte.class || type == Byte.class) {
            schema.put("type", "integer");
        } else if (type == float.class || type == Float.class ||
                   type == double.class || type == Double.class) {
            schema.put("type", "number");
        } else if (type == boolean.class || type == Boolean.class) {
            schema.put("type", "boolean");
        } else {
            schema.put("type", "string");
        }
        
        return schema;
    }

    /**
     * Check if type is primitive or wrapper.
     */
    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() || 
               type == Integer.class || type == Long.class || 
               type == Double.class || type == Float.class ||
               type == Boolean.class || type == Character.class ||
               type == Byte.class || type == Short.class;
    }

    /**
     * Create default object schema.
     */
    private JsonNode createDefaultObjectSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        return schema;
    }
}

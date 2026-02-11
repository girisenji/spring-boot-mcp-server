package com.girisenji.ai.model;

import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * Metadata required to execute a discovered tool by invoking the actual
 * endpoint.
 */
public record ToolExecutionMetadata(
        String endpoint,
        HttpMethod method,
        Map<String, ParameterMapping> parameterMappings,
        String contentType,
        String discoveryType) {

    /**
     * Describes how to map a tool argument to an HTTP request parameter.
     */
    public record ParameterMapping(
            String name,
            ParameterLocation location,
            Class<?> type,
            boolean required) {

        public enum ParameterLocation {
            PATH,
            QUERY,
            HEADER,
            BODY
        }
    }
}

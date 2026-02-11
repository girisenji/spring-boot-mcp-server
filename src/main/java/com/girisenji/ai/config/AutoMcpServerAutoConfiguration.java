package com.girisenji.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisenji.ai.controller.McpController;
import com.girisenji.ai.controller.ToolManagementController;
import com.girisenji.ai.discovery.EndpointDiscoveryService;
import com.girisenji.ai.discovery.GraphQLDiscoveryService;
import com.girisenji.ai.discovery.OpenApiDiscoveryService;
import com.girisenji.ai.discovery.RestEndpointDiscoveryService;
import com.girisenji.ai.service.McpToolExecutor;
import com.girisenji.ai.service.McpToolRegistry;
import com.girisenji.ai.service.ToolApprovalService;
import com.girisenji.ai.util.JsonSchemaGenerator;
import graphql.schema.GraphQLSchema;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Auto-configuration for Auto MCP Server.
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "auto-mcp-server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AutoMcpServerProperties.class)
public class AutoMcpServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AutoMcpServerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaGenerator jsonSchemaGenerator(ObjectMapper objectMapper) {
        return new JsonSchemaGenerator(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "auto-mcp-server.discovery", name = "openapi-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
    public OpenApiDiscoveryService openApiDiscoveryService(
            AutoMcpServerProperties properties,
            ObjectMapper objectMapper,
            Optional<OpenAPI> openAPI) {

        if (openAPI.isEmpty()) {
            log.debug("OpenAPI bean not found, skipping OpenAPI discovery");
            return null;
        }

        log.info("Configuring OpenAPI discovery service");
        return new OpenApiDiscoveryService(properties, objectMapper, openAPI.get());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "auto-mcp-server.discovery", name = "rest-enabled", havingValue = "true", matchIfMissing = true)
    public RestEndpointDiscoveryService restEndpointDiscoveryService(
            AutoMcpServerProperties properties,
            ApplicationContext applicationContext,
            JsonSchemaGenerator schemaGenerator) {

        log.info("Configuring REST endpoint discovery service");
        return new RestEndpointDiscoveryService(properties, applicationContext, schemaGenerator);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "auto-mcp-server.discovery", name = "graphql-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "graphql.schema.GraphQLSchema")
    public GraphQLDiscoveryService graphQLDiscoveryService(
            AutoMcpServerProperties properties,
            ObjectMapper objectMapper,
            Optional<GraphQLSchema> graphQLSchema) {

        if (graphQLSchema.isEmpty()) {
            log.debug("GraphQL schema not found, skipping GraphQL discovery");
            return null;
        }

        log.info("Configuring GraphQL discovery service");
        return new GraphQLDiscoveryService(properties, objectMapper, graphQLSchema.get());
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolApprovalService toolApprovalService(
            AutoMcpServerProperties properties,
            ResourceLoader resourceLoader) {

        log.info("Configuring tool approval service (config-driven mode)");
        return new ToolApprovalService(properties, resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolRegistry mcpToolRegistry(
            List<EndpointDiscoveryService> discoveryServices,
            ToolApprovalService approvalService,
            AutoMcpServerProperties properties) {
        // Filter out null services
        List<EndpointDiscoveryService> validServices = new ArrayList<>();
        for (EndpointDiscoveryService service : discoveryServices) {
            if (service != null) {
                validServices.add(service);
            }
        }

        log.info("Configuring MCP tool registry with {} discovery services", validServices.size());
        return new McpToolRegistry(validServices, approvalService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolExecutor mcpToolExecutor(
            ApplicationContext applicationContext,
            ObjectMapper objectMapper,
            AutoMcpServerProperties properties) {

        String baseUrl = properties.baseUrl();
        log.info("Configuring MCP tool executor with base URL: {}", baseUrl);
        return new McpToolExecutor(applicationContext, objectMapper, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolManagementController toolManagementController(
            McpToolRegistry toolRegistry,
            ToolApprovalService approvalService) {

        log.info("Configuring tool management controller");
        return new ToolManagementController(toolRegistry, approvalService);
    }

    /**
     * Configuration for initializing the tool registry on startup.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "auto-mcp-server.performance", name = "eager-init", havingValue = "true")
    public static class McpToolRegistryInitializer {

        private static final Logger log = LoggerFactory.getLogger(McpToolRegistryInitializer.class);

        public McpToolRegistryInitializer(McpToolRegistry toolRegistry) {
            log.info("Eagerly initializing MCP tool registry");
            toolRegistry.initialize();
        }
    }
}

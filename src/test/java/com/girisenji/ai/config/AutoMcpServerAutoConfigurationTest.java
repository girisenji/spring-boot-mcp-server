package com.girisenji.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisenji.ai.controller.McpController;
import com.girisenji.ai.service.McpToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AutoMcpServerAutoConfigurationTest {

    @Configuration
    static class TestConfiguration {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AutoMcpServerAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void testAutoConfigurationEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AutoMcpServerProperties.class);
            assertThat(context).hasSingleBean(McpToolRegistry.class);
            // Note: McpController needs component scan, we'll just verify the core beans
        });
    }

    @Test
    void testAutoConfigurationDisabled() {
        contextRunner
                .withPropertyValues("auto-mcp-server.enabled=false")
                .run(context -> {
                    // When disabled, beans should not be created
                    assertThat(context).doesNotHaveBean(McpToolRegistry.class);
                });
    }

    @Test
    void testPropertiesLoaded() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AutoMcpServerProperties.class);
            AutoMcpServerProperties props = context.getBean(AutoMcpServerProperties.class);
            // Verify defaults
            assertThat(props.enabled()).isTrue();
            assertThat(props.endpoint()).isEqualTo("/mcp");
            assertThat(props.discovery().openapiEnabled()).isTrue();
            assertThat(props.discovery().restEnabled()).isTrue();
            assertThat(props.discovery().graphqlEnabled()).isTrue();
        });
    }
}

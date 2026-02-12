package io.github.girisenji.ai.service;

import io.github.girisenji.ai.config.AutoMcpServerProperties;
import io.github.girisenji.ai.model.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ToolConfigurationService}.
 */
class ToolConfigurationServiceTest {

  @TempDir
  Path tempDir;

  private ResourceLoader resourceLoader;
  private RateLimitService rateLimitService;
  private AutoMcpServerProperties properties;

  @BeforeEach
  void setUp() {
    resourceLoader = new FileSystemResourceLoader();
    rateLimitService = new RateLimitService(100);
  }

  @Test
  void shouldLoadSimpleToolConfiguration() throws IOException {
    // Given
    String yaml = """
        approvedTools:
          - getUser
          - listUsers
          - createUser
        """;
    Path configFile = createConfigFile(yaml);
    properties = createProperties(configFile);

    // When
    ToolConfigurationService service = new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    // Then
    assertThat(service.getApprovedCount()).isEqualTo(3);
    assertTrue(service.isToolApproved("getUser"));
    assertTrue(service.isToolApproved("listUsers"));
    assertTrue(service.isToolApproved("createUser"));
    assertFalse(service.isToolApproved("deleteUser"));
  }

  @Test
  void shouldLoadToolConfigurationWithRateLimits() throws IOException {
    // Given
    String yaml = """
        approvedTools:
          - name: getUser
            rateLimit:
              requests: 100
              window: PT1H
          - name: listUsers
            rateLimit:
              requests: 50
              window: PT30M
          - simpleToolNoLimit
        """;
    Path configFile = createConfigFile(yaml);
    properties = createProperties(configFile);

    // When
    ToolConfigurationService service = new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    // Then
    assertThat(service.getApprovedCount()).isEqualTo(3);

    // Check rate limit for getUser
    Optional<RateLimitConfig> getUserLimit = service.getRateLimitConfig("getUser");
    assertTrue(getUserLimit.isPresent());
    assertThat(getUserLimit.get().maxRequests()).isEqualTo(100);
    assertThat(getUserLimit.get().window()).isEqualTo(Duration.ofHours(1));

    // Check rate limit for listUsers
    Optional<RateLimitConfig> listUsersLimit = service.getRateLimitConfig("listUsers");
    assertTrue(listUsersLimit.isPresent());
    assertThat(listUsersLimit.get().maxRequests()).isEqualTo(50);
    assertThat(listUsersLimit.get().window()).isEqualTo(Duration.ofMinutes(30));

    // Check tool without rate limit
    Optional<RateLimitConfig> simpleToolLimit = service.getRateLimitConfig("simpleToolNoLimit");
    assertFalse(simpleToolLimit.isPresent());
  }

  @Test
  void shouldHandleEmptyConfigFile() throws IOException {
    // Given
    String yaml = """
        approvedTools: []
        """;
    Path configFile = createConfigFile(yaml);
    properties = createProperties(configFile);

    // When
    ToolConfigurationService service = new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    // Then
    assertThat(service.getApprovedCount()).isEqualTo(0);
    assertThat(service.getApprovedToolNames()).isEmpty();
  }

  @Test
  void shouldHandleMissingConfigFile() {
    // Given
    properties = createProperties(tempDir.resolve("nonexistent.yml"));

    // When
    ToolConfigurationService service = new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    // Then - Should not crash, just log warning
    assertThat(service.getApprovedCount()).isEqualTo(0);
    assertThat(service.getApprovedToolNames()).isEmpty();
  }

  @Test
  void shouldHandleNullConfigFile() {
    // Given
    properties = createPropertiesWithNullConfig();

    // When
    ToolConfigurationService service = new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    // Then - Should not crash, just log warning
    assertThat(service.getApprovedCount()).isEqualTo(0);
    assertThat(service.getApprovedToolNames()).isEmpty();
  }

  @Test
  void shouldHandleInvalidYaml() throws IOException {
    // Given
    String invalidYaml = """
        approvedTools:
          - name: tool1
            rateLimit:
              invalid_field: 123
        """;
    Path configFile = createConfigFile(invalidYaml);
    properties = createProperties(configFile);

    // When/Then - Should not crash, but may skip invalid entries
    assertDoesNotThrow(() -> new ToolConfigurationService(properties, resourceLoader, rateLimitService));
  }

  @Test
  void shouldReloadConfiguration() throws IOException {
    // Given
    String yaml1 = """
        approvedTools:
          - tool1
          - tool2
        """;
    Path configFile = createConfigFile(yaml1);
    properties = createProperties(configFile);
    ToolConfigurationService service = new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    assertThat(service.getApprovedCount()).isEqualTo(2);

    // When - Update config file
    String yaml2 = """
        approvedTools:
          - tool1
          - tool2
          - tool3
          - tool4
        """;
    Files.writeString(configFile, yaml2);
    service.reloadApprovedTools();

    // Then
    assertThat(service.getApprovedCount()).isEqualTo(4);
    assertTrue(service.isToolApproved("tool3"));
    assertTrue(service.isToolApproved("tool4"));
  }

  @Test
  void shouldReturnUnmodifiableSet() throws IOException {
    // Given
    String yaml = """
        approvedTools:
          - tool1
        """;
    Path configFile = createConfigFile(yaml);
    properties = createProperties(configFile);
    ToolConfigurationService service = new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    // When/Then
    assertThrows(UnsupportedOperationException.class, () -> {
      service.getApprovedToolNames().add("newTool");
    });
  }

  @Test
  void shouldRegisterRateLimitsWithService() throws IOException {
    // Given
    String yaml = """
        approvedTools:
          - name: testTool
            rateLimit:
              requests: 5
              window: PT1M
        """;
    Path configFile = createConfigFile(yaml);
    properties = createProperties(configFile);

    // When
    new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    // Then - Rate limit should be enforced by RateLimitService
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimitService.allowRequest("testTool", "127.0.0.1"),
          "Request " + (i + 1) + " should be allowed");
    }
    assertFalse(rateLimitService.allowRequest("testTool", "127.0.0.1"),
        "Request 6 should be denied");
  }

  @Test
  void shouldHandleMixedFormatConfiguration() throws IOException {
    // Given
    String yaml = """
        approvedTools:
          - simpleTool1
          - name: complexTool1
            rateLimit:
              requests: 10
              window: PT5M
          - simpleTool2
          - name: complexTool2
            rateLimit:
              requests: 20
              window: PT10M
        """;
    Path configFile = createConfigFile(yaml);
    properties = createProperties(configFile);

    // When
    ToolConfigurationService service = new ToolConfigurationService(properties, resourceLoader, rateLimitService);

    // Then
    assertThat(service.getApprovedCount()).isEqualTo(4);
    assertTrue(service.isToolApproved("simpleTool1"));
    assertTrue(service.isToolApproved("simpleTool2"));
    assertTrue(service.isToolApproved("complexTool1"));
    assertTrue(service.isToolApproved("complexTool2"));

    assertThat(service.getRateLimitConfig("complexTool1")).isPresent();
    assertThat(service.getRateLimitConfig("complexTool2")).isPresent();
    assertThat(service.getRateLimitConfig("simpleTool1")).isEmpty();
    assertThat(service.getRateLimitConfig("simpleTool2")).isEmpty();
  }

  // Helper methods

  private Path createConfigFile(String yaml) throws IOException {
    Path configFile = tempDir.resolve("test-config.yml");
    Files.writeString(configFile, yaml);
    return configFile;
  }

  private AutoMcpServerProperties createProperties(Path configFile) {
    AutoMcpServerProperties.Tools tools = new AutoMcpServerProperties.Tools(
        new String[] { "/**" },
        new String[] { "/actuator/**" },
        100,
        true,
        "file:" + configFile.toAbsolutePath(),
        AutoMcpServerProperties.Tools.DuplicateToolStrategy.FIRST_WINS,
        false);

    return new AutoMcpServerProperties(
        true,
        "/mcp",
        "http://localhost:8080",
        new AutoMcpServerProperties.Discovery(),
        tools,
        new AutoMcpServerProperties.Performance(),
        new AutoMcpServerProperties.RateLimiting(),
        new AutoMcpServerProperties.Execution("PT30S", "PT5S", "10MB", "10MB"),
        new AutoMcpServerProperties.Audit());
  }

  private AutoMcpServerProperties createPropertiesWithNullConfig() {
    AutoMcpServerProperties.Tools tools = new AutoMcpServerProperties.Tools(
        new String[] { "/**" },
        new String[] { "/actuator/**" },
        100,
        true,
        null, // null config file
        AutoMcpServerProperties.Tools.DuplicateToolStrategy.FIRST_WINS,
        false);

    return new AutoMcpServerProperties(
        true,
        "/mcp",
        "http://localhost:8080",
        new AutoMcpServerProperties.Discovery(),
        tools,
        new AutoMcpServerProperties.Performance(),
        new AutoMcpServerProperties.RateLimiting(),
        new AutoMcpServerProperties.Execution("PT30S", "PT5S", "10MB", "10MB"),
        new AutoMcpServerProperties.Audit());
  }
}

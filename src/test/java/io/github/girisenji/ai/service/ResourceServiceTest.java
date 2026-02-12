package io.github.girisenji.ai.service;

import io.github.girisenji.ai.mcp.McpProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResourceService.
 */
class ResourceServiceTest {

    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceService();
    }

    @Test
    void testInitializationCreatesDefaultResources() {
        List<McpProtocol.Resource> resources = resourceService.listResources();

        // Should have at least the default server config resource
        assertFalse(resources.isEmpty(), "Should have default resources");
        assertTrue(resources.stream()
                .anyMatch(r -> r.uri().equals("mcp://server/config")),
                "Should have server config resource");
    }

    @Test
    void testListResourcesReturnsAllResources() {
        List<McpProtocol.Resource> resources = resourceService.listResources();

        assertNotNull(resources);
        // Check that default resource exists
        McpProtocol.Resource serverConfig = resources.stream()
                .filter(r -> r.uri().equals("mcp://server/config"))
                .findFirst()
                .orElse(null);

        assertNotNull(serverConfig);
        assertEquals("Server Configuration", serverConfig.name());
        assertEquals("application/json", serverConfig.mimeType());
    }

    @Test
    void testRegisterResource() {
        resourceService.registerResource(
                "test://example",
                "Test Resource",
                "A test resource",
                "text/plain",
                () -> McpProtocol.ResourceContents.text(
                        "test://example",
                        "text/plain",
                        "Hello, World!"));

        List<McpProtocol.Resource> resources = resourceService.listResources();
        assertTrue(resources.stream()
                .anyMatch(r -> r.uri().equals("test://example")),
                "Should contain newly registered resource");
    }

    @Test
    void testReadResourceSuccess() {
        resourceService.registerResource(
                "test://data",
                "Test Data",
                "Test data resource",
                "text/plain",
                () -> McpProtocol.ResourceContents.text(
                        "test://data",
                        "text/plain",
                        "Test content"));

        McpProtocol.ResourceContents contents = resourceService.readResource("test://data");

        assertNotNull(contents);
        assertEquals("test://data", contents.uri());
        assertEquals("text/plain", contents.mimeType());
        assertEquals("Test content", contents.text());
    }

    @Test
    void testReadResourceNotFound() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resourceService.readResource("test://nonexistent"));

        assertTrue(exception.getMessage().contains("Resource not found"));
    }

    @Test
    void testReadDefaultServerConfigResource() {
        McpProtocol.ResourceContents contents = resourceService.readResource("mcp://server/config");

        assertNotNull(contents);
        assertEquals("mcp://server/config", contents.uri());
        assertEquals("application/json", contents.mimeType());
        assertNotNull(contents.text());
        assertTrue(contents.text().contains("protocol"));
        assertTrue(contents.text().contains("MCP"));
    }

    @Test
    void testResourceProviderThrowsException() {
        resourceService.registerResource(
                "test://error",
                "Error Resource",
                "Resource that throws error",
                "text/plain",
                () -> {
                    throw new RuntimeException("Provider error");
                });

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> resourceService.readResource("test://error"));

        assertTrue(exception.getMessage().contains("Failed to read resource"));
    }

    @Test
    void testRegisterMultipleResources() {
        resourceService.registerResource(
                "test://resource1",
                "Resource 1",
                "First test resource",
                "text/plain",
                () -> McpProtocol.ResourceContents.text("test://resource1", "text/plain", "Content 1"));

        resourceService.registerResource(
                "test://resource2",
                "Resource 2",
                "Second test resource",
                "application/json",
                () -> McpProtocol.ResourceContents.text("test://resource2", "application/json",
                        "{\"data\":\"value\"}"));

        List<McpProtocol.Resource> resources = resourceService.listResources();

        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("test://resource1")));
        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("test://resource2")));
    }

    @Test
    void testResourceWithBlobContent() {
        String testData = java.util.Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3, 4, 5 });
        resourceService.registerResource(
                "test://binary",
                "Binary Resource",
                "Binary test resource",
                "application/octet-stream",
                () -> McpProtocol.ResourceContents.blob(
                        "test://binary",
                        "application/octet-stream",
                        testData));

        McpProtocol.ResourceContents contents = resourceService.readResource("test://binary");

        assertNotNull(contents);
        assertEquals("test://binary", contents.uri());
        assertEquals("application/octet-stream", contents.mimeType());
        assertEquals(testData, contents.blob());
        assertNull(contents.text(), "Blob resource should not have text");
    }

    @Test
    void testResourceMetadata() {
        resourceService.registerResource(
                "test://metadata",
                "Metadata Resource",
                "Resource with detailed metadata",
                "application/json",
                () -> McpProtocol.ResourceContents.text(
                        "test://metadata",
                        "application/json",
                        "{}"));

        List<McpProtocol.Resource> resources = resourceService.listResources();
        McpProtocol.Resource metadata = resources.stream()
                .filter(r -> r.uri().equals("test://metadata"))
                .findFirst()
                .orElse(null);

        assertNotNull(metadata);
        assertEquals("Metadata Resource", metadata.name());
        assertEquals("Resource with detailed metadata", metadata.description());
        assertEquals("application/json", metadata.mimeType());
    }
}

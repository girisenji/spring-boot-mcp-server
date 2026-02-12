package io.github.girisenji.ai.service;

import io.github.girisenji.ai.mcp.McpProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PromptService.
 */
class PromptServiceTest {

    private PromptService promptService;

    @BeforeEach
    void setUp() {
        promptService = new PromptService();
    }

    @Test
    void testInitializationCreatesDefaultPrompts() {
        List<McpProtocol.Prompt> prompts = promptService.listPrompts();

        // Should have default prompts
        assertFalse(prompts.isEmpty(), "Should have default prompts");
        assertTrue(prompts.stream()
                .anyMatch(p -> p.name().equals("welcome")),
                "Should have welcome prompt");
        assertTrue(prompts.stream()
                .anyMatch(p -> p.name().equals("tool-usage-help")),
                "Should have tool-usage-help prompt");
    }

    @Test
    void testListPromptsReturnsAllPrompts() {
        List<McpProtocol.Prompt> prompts = promptService.listPrompts();

        assertNotNull(prompts);
        assertTrue(prompts.size() >= 2, "Should have at least 2 default prompts");
    }

    @Test
    void testGetWelcomePrompt() {
        McpProtocol.GetPromptResult result = promptService.getPrompt("welcome", Map.of());

        assertNotNull(result);
        assertNotNull(result.description());
        assertFalse(result.messages().isEmpty());

        McpProtocol.PromptMessage message = result.messages().get(0);
        assertEquals("user", message.role());
        assertNotNull(message.content());
        assertTrue(message.content().text().contains("Welcome"));
        assertTrue(message.content().text().contains("MCP"));
    }

    @Test
    void testGetToolUsageHelpPromptWithoutArguments() {
        McpProtocol.GetPromptResult result = promptService.getPrompt("tool-usage-help", Map.of());

        assertNotNull(result);
        assertFalse(result.messages().isEmpty());

        McpProtocol.PromptMessage message = result.messages().get(0);
        assertTrue(message.content().text().contains("any tool"));
    }

    @Test
    void testGetToolUsageHelpPromptWithToolName() {
        Map<String, Object> args = Map.of("toolName", "getUserById");
        McpProtocol.GetPromptResult result = promptService.getPrompt("tool-usage-help", args);

        assertNotNull(result);
        assertFalse(result.messages().isEmpty());

        McpProtocol.PromptMessage message = result.messages().get(0);
        assertTrue(message.content().text().contains("getUserById"));
    }

    @Test
    void testGetPromptNotFound() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> promptService.getPrompt("nonexistent", Map.of()));

        assertTrue(exception.getMessage().contains("Prompt not found"));
    }

    @Test
    void testRegisterCustomPrompt() {
        promptService.registerPrompt(
                "custom-prompt",
                "A custom test prompt",
                List.of(),
                args -> new McpProtocol.GetPromptResult(
                        "Custom prompt description",
                        List.of(new McpProtocol.PromptMessage(
                                "user",
                                McpProtocol.Content.text("Custom prompt message")))));

        List<McpProtocol.Prompt> prompts = promptService.listPrompts();
        assertTrue(prompts.stream()
                .anyMatch(p -> p.name().equals("custom-prompt")));
    }

    @Test
    void testGetCustomPrompt() {
        promptService.registerPrompt(
                "test-prompt",
                "Test prompt",
                List.of(),
                args -> new McpProtocol.GetPromptResult(
                        "Test description",
                        List.of(new McpProtocol.PromptMessage(
                                "user",
                                McpProtocol.Content.text("Test message")))));

        McpProtocol.GetPromptResult result = promptService.getPrompt("test-prompt", Map.of());

        assertNotNull(result);
        assertEquals("Test description", result.description());
        assertEquals(1, result.messages().size());
        assertEquals("Test message", result.messages().get(0).content().text());
    }

    @Test
    void testPromptWithArguments() {
        promptService.registerPrompt(
                "parameterized-prompt",
                "Prompt with parameters",
                List.of(
                        new McpProtocol.PromptArgument("name", "User name", true),
                        new McpProtocol.PromptArgument("age", "User age", false)),
                args -> {
                    String name = (String) args.get("name");
                    String message = "Hello, " + name + "!";
                    return new McpProtocol.GetPromptResult(
                            "Personalized greeting",
                            List.of(new McpProtocol.PromptMessage(
                                    "user",
                                    McpProtocol.Content.text(message))));
                });

        Map<String, Object> args = Map.of("name", "Alice");
        McpProtocol.GetPromptResult result = promptService.getPrompt("parameterized-prompt", args);

        assertNotNull(result);
        assertTrue(result.messages().get(0).content().text().contains("Alice"));
    }

    @Test
    void testPromptMetadata() {
        List<McpProtocol.Prompt> prompts = promptService.listPrompts();
        McpProtocol.Prompt welcome = prompts.stream()
                .filter(p -> p.name().equals("welcome"))
                .findFirst()
                .orElse(null);

        assertNotNull(welcome);
        assertEquals("welcome", welcome.name());
        assertNotNull(welcome.description());
        assertNotNull(welcome.arguments());
        assertTrue(welcome.arguments().isEmpty(), "Welcome prompt should have no arguments");
    }

    @Test
    void testToolUsageHelpPromptMetadata() {
        List<McpProtocol.Prompt> prompts = promptService.listPrompts();
        McpProtocol.Prompt toolHelp = prompts.stream()
                .filter(p -> p.name().equals("tool-usage-help"))
                .findFirst()
                .orElse(null);

        assertNotNull(toolHelp);
        assertEquals("tool-usage-help", toolHelp.name());
        assertNotNull(toolHelp.arguments());
        assertEquals(1, toolHelp.arguments().size());

        McpProtocol.PromptArgument arg = toolHelp.arguments().get(0);
        assertEquals("toolName", arg.name());
        assertFalse(arg.required(), "toolName argument should be optional");
    }

    @Test
    void testPromptProviderThrowsException() {
        promptService.registerPrompt(
                "error-prompt",
                "Prompt that throws error",
                List.of(),
                args -> {
                    throw new RuntimeException("Provider error");
                });

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> promptService.getPrompt("error-prompt", Map.of()));

        assertTrue(exception.getMessage().contains("Failed to get prompt"));
    }

    @Test
    void testGetPromptWithNullArguments() {
        McpProtocol.GetPromptResult result = promptService.getPrompt("welcome", null);

        // Should handle null arguments gracefully
        assertNotNull(result);
        assertFalse(result.messages().isEmpty());
    }
}

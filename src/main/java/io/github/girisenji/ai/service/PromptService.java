package io.github.girisenji.ai.service;

import io.github.girisenji.ai.mcp.McpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing MCP Prompts.
 * Prompts are pre-defined prompt templates that help AI agents use tools
 * effectively.
 * 
 * <p>
 * Examples of prompts:
 * <ul>
 * <li>API usage examples ("How to call the getUser endpoint")</li>
 * <li>Common workflows ("How to create a new order")</li>
 * <li>Best practices ("Error handling patterns")</li>
 * </ul>
 */
@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    private final Map<String, McpProtocol.Prompt> prompts;
    private final Map<String, PromptProvider> promptProviders;

    public PromptService() {
        this.prompts = new ConcurrentHashMap<>();
        this.promptProviders = new ConcurrentHashMap<>();
        initializeDefaultPrompts();
    }

    /**
     * Initialize default prompts for common use cases.
     */
    private void initializeDefaultPrompts() {
        // Welcome prompt - introduces the MCP server
        registerPrompt(
                "welcome",
                "Introduction to available MCP tools",
                List.of(),
                args -> {
                    String welcomeMessage = """
                            Welcome to the Spring Boot MCP Server!

                            This server exposes your Spring Boot application's REST and GraphQL endpoints as MCP tools.

                            Available capabilities:
                            - Tools: Execute REST/GraphQL operations
                            - Resources: Read configuration and data
                            - Prompts: Get helpful guidance on using the tools

                            Use tools/list to see all available tools.
                            Use resources/list to see available resources.
                            """;

                    return new McpProtocol.GetPromptResult(
                            "Introduction to the MCP server and its capabilities",
                            List.of(new McpProtocol.PromptMessage(
                                    "user",
                                    McpProtocol.Content.text(welcomeMessage))));
                });

        // Tool usage help prompt
        registerPrompt(
                "tool-usage-help",
                "How to use MCP tools effectively",
                List.of(new McpProtocol.PromptArgument("toolName", "Specific tool to get help for", false)),
                args -> {
                    String toolName = (String) args.getOrDefault("toolName", "any tool");
                    String helpMessage = String.format("""
                            How to use %s:

                            1. Check the tool schema using tools/list to understand required parameters
                            2. Prepare your input according to the inputSchema
                            3. Call the tool using tools/call with proper arguments
                            4. Handle the response or errors appropriately

                            Tips:
                            - All tools execute via HTTP requests to your actual endpoints
                            - Rate limiting may apply (check approved-tools.yml)
                            - Execution timeouts are enforced for reliability
                            - All actions are audit logged for security
                            """, toolName);

                    return new McpProtocol.GetPromptResult(
                            "Guidance on using MCP tools effectively",
                            List.of(new McpProtocol.PromptMessage(
                                    "user",
                                    McpProtocol.Content.text(helpMessage))));
                });

        log.info("Initialized {} default prompts", prompts.size());
    }

    /**
     * Register a prompt with a provider function.
     * 
     * @param name        Unique name for the prompt
     * @param description Description of what the prompt provides
     * @param arguments   List of arguments the prompt accepts
     * @param provider    Function that generates the prompt messages
     */
    public void registerPrompt(
            String name,
            String description,
            List<McpProtocol.PromptArgument> arguments,
            PromptProvider provider) {

        McpProtocol.Prompt prompt = new McpProtocol.Prompt(name, description, arguments);
        prompts.put(name, prompt);
        promptProviders.put(name, provider);
        log.debug("Registered prompt: {}", name);
    }

    /**
     * Get all available prompts.
     * 
     * @return List of prompt definitions
     */
    public List<McpProtocol.Prompt> listPrompts() {
        return new ArrayList<>(prompts.values());
    }

    /**
     * Get a specific prompt with arguments.
     * 
     * @param name      Prompt name
     * @param arguments Arguments to pass to the prompt
     * @return Prompt result with messages
     * @throws IllegalArgumentException if prompt not found
     */
    public McpProtocol.GetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        PromptProvider provider = promptProviders.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Prompt not found: " + name);
        }

        try {
            log.debug("Getting prompt: {} with arguments: {}", name, arguments);
            return provider.provide(arguments != null ? arguments : Map.of());
        } catch (Exception e) {
            log.error("Failed to get prompt: {}", name, e);
            throw new RuntimeException("Failed to get prompt: " + e.getMessage(), e);
        }
    }

    /**
     * Functional interface for prompt providers.
     */
    @FunctionalInterface
    public interface PromptProvider {
        McpProtocol.GetPromptResult provide(Map<String, Object> arguments);
    }
}

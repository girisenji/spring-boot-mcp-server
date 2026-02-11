# Spring Boot MCP Server

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-green)](https://spring.io/projects/spring-boot)

A Spring Boot starter library that enables your application to serve as an **MCP (Model Context Protocol) server**, allowing AI agents like Claude to interact with your custom tools.

## 🚀 Features

- **Auto-Configuration**: Add dependency and get MCP server out-of-the-box
- **SSE Transport**: Implements MCP protocol over Server-Sent Events
- **Config-Driven Security**: Tool approval via version-controlled YAML files
- **Custom Tools**: Simple interface for creating tools
- **Tool Discovery**: Automatic discovery of Spring beans implementing `McpTool`
- **Admin UI**: Web interface for tool management and discovery
- **Runtime Reload**: Reload approved tools without restart
- **Zero Dependencies**: No external tools or agents required

## 📦 Installation

### Maven
```xml
<dependency>
    <groupId>com.girisenji.ai</groupId>
    <artifactId>spring-boot-mcp-server</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```gradle
implementation 'com.girisenji.ai:spring-boot-mcp-server:1.0.0'
```

## 🎯 Quick Start

### 1. Add Dependency

Add to your Spring Boot 3.2+ application:

```xml
<dependency>
    <groupId>com.girisenji.ai</groupId>
    <artifactId>spring-boot-mcp-server</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Create a Tool

Implement the `McpTool` interface:

```java
@Component
public class GreetingTool implements McpTool {
    
    @Override
    public String getName() {
        return "greet";
    }
    
    @Override
    public String getDescription() {
        return "Generate a greeting message";
    }
    
    Application Properties (Optional)

```yaml
mcp:
  server:
    enabled: true  # Enable/disable MCP server (default: true)
    
    # Server metadata
    server-info:
      name: "My MCP Server"
      version: "1.0.0"
      description: "Custom MCP server"
    
    # Tool approval configuration
    tool-approval:
      approved-tools-config: "classpath:approved-tools.yml"
```

### Tool Approval (Required)

**All tools must be explicitly approved** via YAML configuration. Create `src/main/resources/approved-tools.yml`:

```yaml
approvedTools:
  - tool_name_1
  - tool_name_2
  - greet
```

**Security Model:**
- ✅ **Config-Driven Only**: Tools approved via version-controlled YAML
- ✅ **Deny by Default**: No tools approved unless explicitly listed
- ✅ **Audit Trail**: Changes tracked in Git history
- ✅ **Runtime Reload**: Update configuration and reload without restart

**External Configuration:**

For production, use external config files:

```yaml
mcp:
  server:
    tool-approval:
      approved-tools-config: "file:/etc/mcp/approved-tools.yml"
```

```bash
# Environment variable
export MCP_SERVER_TOOL_APPROVAL_APPROVED_TOOLS_CONFIG=file:/config/approved-tools.yml
java -jar app.jar
    openapi-enabled: true   # Discover from OpenAPI specs
    rest-enabled: true      # Discover REST endpoints
    graphql-enabled: true   # Discover GraphQL endpoints
```

### Tool Filtering

```yaml
auto-mcp-server:
  tools:
    include-patterns:
      - "/api/**"           # Include API endpoints
      - "/v1/**"            # Include v1 endpoints
    exclude-patterns:
      - "/actuator/**"      # Exclude actuator endpoints
      - "/error"            # Exclude error endpoint
      - "/internal/**"      # Exclude internal endpoints
    max-tool-name-length: 100
    use-operation-id-as-tool-name: true
```

### Complete Example

```yaml
auto-mcp-server:
  enabled: true
  endpoint: /mcp
  eager-init: true
  
  discovery:
    openapi-enabled: true
    rest-enabled: true
    graphql-enabled: true
  
  tools:
    # Discovery filtering
    include-patterns: "/**"
    exclude-patterns: 
      - "/actuator/**"
      - "/error"
      - "/swagger-ui/**"
      - "/v3/api-docs/**"
    
    # Tool approval (CONFIG_BASED recommended for production)
    approval-mode: config-based
    approval-config-file: classpath:approved-tools.yml
    
    # Tool naming
    max-tool-name-length: 100
    use-operation-id-as-tool-name: true
```

## 🛡️ Tool Approval & Management

> **⚠️ Important**: The `/mcp/admin/tools` management API is designed for **DEVELOPMENT workflow only**. In production, use `CONFIG_BASED` mode with `approved-tools.yml` versioned in Git.

### Development Workflow

Use the management API during development to discover and approve tools, then export to YAML for production:

1. **Development**: Use `manual` mode, approve tools via API
2. **Export**: Save approved tools to `approved-tools.yml`
3. **Production**: Use `config-based` mode with the YAML file

### Management Endpoints

The library provides administrative endpoints for managing tool approvals:

```bash
# Get approval summary
GET /mcp/admin/tools/summary

# List all discovered tools
GET /mcp/admin/tools/discovered

# List approved tools (exposed to agents)
GET /mcp/admin/tools/approved

# List pending tools (need approval)
GET /mcp/admin/tools/pending

# Get specific tool status
GET /mcp/admin/tools/{toolName}/status

# Approve a tool
POST /mcp/admin/tools/{toolName}/approve

# Reject a tool
POST /mcp/admin/tools/{toolName}/reject

# Bulk approve by pattern
POST /mcp/admin/tools/approve-pattern

# Refresh discovery
POST /mcp/admin/tools/refresh
```

### �️ Tool Management

### Admin UI

Access the web-based admin interface:

```bash
open http://localhost:8080/mcp/admin/tools
```

Features:
- View all discovered tools
- See approval status
- Export tool list to YAML
- Reload configuration

### Management API

```bash
# List all tools with approval status (paginated)
GET /mcp/admin/tools/api/tools?page=0&size=20

# Reload approved tools from configuration
POST /mcp/admin/tools/reload

# Export discovered tools as YAML template
GET /mcp/admin/tools/export/yaml
```

### Development Workflow

1. **Develop Tools**: Create `@Component` classes implementing `McpTool`
2. **Discover**: Visit admin UI to see discovered tools
3. **Export**: Download YAML template with all tools
4. **Approve**: Edit `approved-tools.yml` to approve needed tools
5. **Reload**: POST to `/mcp/admin/tools/reload` or restart app
```java
@Autowired
private McpToolRegistry toolRegistry;

public void refreshTools() {
    toolRegistry.refreshTools();
}
```

## 📖 How It Works

### Discovery Process

1. **OpenAPI Discovery** (if available)
   - Parses OpenAPI specification
   - Extracts operations with parameters and schemas
   - Generates tool definitions with proper JSON schemas

2. **REST Discovery** (fallback)
   - Scans `@RestController` beans
   - Analyzes `@RequestMapping` methods
   - Generates schemas from method signatures

3. **GraphQL Discovery** (if available)
   - Introspects GraphQL schema
   - Discovers queries and mutations
   - Maps GraphQL types to JSON schemas

### Approval Workflow

```
┌─────────────────┐
│  API Endpoint   │
│   Discovered    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      ┌──────────────┐
│ Determine Mode  │─────▶│ CONFIG_BASED │──▶ ✅ PRODUCTION (approved-tools.yml)
└────────┬────────┘      └──────────────┘
      Creating Custom Tools

### Tool Interface

```java
public interface McpTool {
    String getName();           // Unique tool identifier
    String getDescription();    // Human-readable description
    JsonNode getInputSchema();  // JSON Schema for parameters
    ToolResult execute(Map<String, Object> arguments);  // Tool logic
}
```

### File Operations Example

```java
@Component
public class ReadFileTool implements McpTool {
    
    @Override
    public String getName() {
        return "read_file";
    }
    
    @Override
    public String getDescription() {
        return "Read contents of a file";
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode path = mapper.createObjectNode();
        path.put("type", "string");
        path.put("description", "File path to read");
        properties.set("path", path);
        
        schema.set("properties", properties);
        schema.set("required", mapper.createArrayNode().add("path"));
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        try {
            String path = (String) arguments.get("path");
            String content = Files.readString(Path.of(path));
            return ToolResult.success(content);
        } catch (IOException e) {
            return ToolResult.error("Failed to read file: " + e.getMessage());
    Architecture

```
┌─────────────────────────────────────────────────┐
│              Spring Boot Application            │
│  ┌───────────────────────────────────────────┐  │
│  │         Auto-Configuration                │  │
│  │  - Discovers @Component McpTool beans     │  │
│  │  - Registers MCP endpoints                │  │
│  │  - Configures tool registry               │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  ┌───────────────┐       ┌───────────────-───┐  │
│  │  McpTool      │       │  Tool Approval    │  │
│  │  Registry     │◄──────┤  Service          │  │
│  │  - Discovery  │       │  - YAML config    │  │
│  │  - Filtering  │       │  - Approval check │  │
│  └───────┬───────┘       └────────────────-──┘  │
│          │                                      │
│          ▼                                      │
│  ┌───────────────────────────────────────────┐  │
│  │        MCP Controller (/mcp/sse)          │  │
│  │  - SSE endpoint                           │  │
│  │  - Protocol handling                      │  │
│  │  - Tool execution                         │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
                      │
                      │ SSE (Server-Sent Events)
                      ▼
              ┌─────────────-─┐
              │  AI Agent     │
              │ (Claude, etc) │
              └─────────────-─┘
```

### Tool Approval Flow

```
┌──────────────────┐
│  Tool Created    │
│  @Component      │
│  McpTool         │
└────────┬─────────┘
         │
         ▼[LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Model Context Protocol](https://modelcontextprotocol.io) - The protocol specification
- [Spring Boot](https://spring.io/projects/spring-boot) - The application framework
- [Anthropic](https://www.anthropic.com) - MCP protocol development

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/girisenji/spring-boot-mcp-server/issues)
- **Discussions**: [GitHub Discussions](https://github.com/girisenji/spring-boot-mcp-server/discussions)
- **Email**: girisenji@gmail.com

## 🗺️ Roadmap

- [x] Config-driven tool approval
- [x] SSE transport implementation
- [x] Tool discovery and registry
- [x] Admin UI
- [x] Runtime configuration reload
- [ ] WebSocket transport support
- [ ] Resource and Prompt support
- [ ] Enhanced metrics and monitoring
- [ ] Rate limiting and quotas
- [ ] Multi-tenant support

## 📚 Learn More

- [Model Context Protocol Specification](https://modelcontextprotocol.io)
- [MCP GitHub Repository](https://github.com/modelcontextprotocol)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)  
**Transport**: Server-Sent Events (SSE)  
**Format**: JSON-RPC 2.0

**Supported Methods**:
- `initialize`Application
Dependencies

The library requires only Spring Boot and Jackson (already included in Spring Boot):

```🔒 Security

**Tool Approval**: All tools are denied by default. Only tools explicitly listed in `approved-tools.yml` are exposed to AI agents.

**Input Validation**: Implement proper validation in your tools:

```java
@Override
public ToolResult execute(Map<String, Object> arguments) {
    // Validate required parameters
    if (!arguments.containsKey("path")) {
        return ToolResult.error("Missing required parameter: path");
    }
    
    String path = (String) arguments.get("path");
    
    // Validate input
    if (path.contains("..")) {
        return ToolResult.error("Path traversal not allowed");
    }
    
    // Safe execution
    return performOperation(path);
}
```

**Authentication**: Integrate with Spring Security for endpoint protection:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/mcp/sse").authenticated()
            .requestMatchers("/mcp/admin/**").hasRole("ADMIN")
        );
        return http.build();
    }
}
```

**HTTPS**: Always use HTTPS in production:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

No additional dependencies required.
**Application.java:**
```java
@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
```

**EchoTool.java:**
```java
@Component
public class EchoTool implements McpTool {
    
    @Override
    public String getName() {
        return "echo";
    }
    
    @Override
    public String getDescription() {
        return "Echoes back the input message";
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "string");
        message.put("description", "Message to echo");
        properties.set("message", message);
        
        schema.set("properties", properties);
        schema.set("required", mapper.createArrayNode().add("message"));
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String message = (String) arguments.get("message");
        return ToolResult.success("Echo: " + message);
    }
}
```

**approved-tools.yml:**
```yaml
approvedTools:
  - echo
```

Run the application and connect AI agents to `http://localhost:8080/mcp/sse`

## 📋 Requirements

- **Java**: 21 or higher
- **Spring Boot**: 3.2 or higher
- **Build Tool**: Maven or Gradle

### Optional Dependencies
- **OpenAPI**: SpringDoc OpenAPI v2 for OpenAPI discovery
- **GraphQL**: Spring GraphQL for GraphQL discovery

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## 🙏 Acknowledgments

- [Model Context Protocol](https://modelcontextprotocol.io) - The protocol specification
- [Spring Boot](https://spring.io/projects/spring-boot) - The application framework
- [SpringDoc OpenAPI](https://springdoc.org) - OpenAPI support

## 📞 Support

For issues, questions, or contributions:
- Create an issue on GitHub
- Contact the maintainers

## 🗺️ Roadmap

- [ ] Tool execution implementation
- [ ] WebSocket transport support
- [ ] Resource and Prompt support
- [ ] Enhanced security features
- [ ] Performance optimizations
- [ ] Metrics and monitoring
- [ ] Sample applications
- [ ] Extended documentation

---

**Made with ❤️ for the AI community**

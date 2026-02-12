# Spring Boot MCP Server

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-green)](https://spring.io/projects/spring-boot)

A Spring Boot starter library that **automatically discovers your REST and GraphQL endpoints** and exposes them as **MCP (Model Context Protocol) tools**, allowing AI agents like Claude to interact with your application's APIs securely.

## 🚀 Features

- **Auto-Discovery**: Automatically discovers REST and GraphQL endpoints from your Spring application
- **OpenAPI Integration**: Uses OpenAPI/Swagger specifications for tool schema generation
- **GraphQL Support**: Discovers GraphQL queries and mutations as separate tools
- **SSE Transport**: Implements MCP protocol over Server-Sent Events
- **Config-Driven Security**: Explicit tool approval via version-controlled YAML - deny by default
- **HTTP Execution**: Executes tools by making HTTP requests to your actual endpoints
- **Admin UI**: Web interface for discovering and managing tools
- **Runtime Reload**: Reload approved tools without restart
- **Auto-Configuration**: Add dependency and get MCP server out-of-the-box
- **Rate Limiting**: Per-tool and per-client IP request throttling
- **Execution Timeouts**: Configurable HTTP request timeouts
- **Request Size Limits**: Protection against memory exhaustion

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
implementation 'io.github.girisenji.ai:spring-boot-mcp-server:1.0.0'
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

### 2. Create REST Endpoints

Create standard Spring REST controllers:

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAll();
    }
    
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.findById(id);
    }
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
  
  # Rate limiting (optional)
  rate-limiting:
    enabled: true
    default-requests-per-hour: 100
  
  # Execution configuration (optional)
  execution:
    default-timeout: PT30S              # 30 seconds read timeout
    default-connect-timeout: PT5S       # 5 seconds connect timeout
    max-request-body-size: 10MB         # Maximum request body size
    max-response-body-size: 10MB        # Maximum response body size
```

### Advanced Tool Configuration

Configure per-tool overrides in `approved-tools.yml` for rate limits, timeouts, and size limits:

```yaml
approvedTools:
  # Simple format (uses all defaults)
  - simpleToolName
  
  # With custom rate limit
  - name: frequentOperation
    rateLimit:
      requests: 1000
      window: PT1H                      # ISO-8601 duration: 1 hour
  
  # With custom timeout
  - name: longRunningTask
    timeout: PT5M                       # 5 minutes for slow operations
    rateLimit:
      requests: 10
      window: PT1H
  
  # With custom size limits
  - name: largeDataUpload
    maxRequestBodySize: 50MB            # Allow larger request
    maxResponseBodySize: 100MB          # Allow larger response
    timeout: PT2M
  
  # Full configuration example
  - name: complexOperation
    timeout: PT1M
    maxRequestBodySize: 25MB
    maxResponseBodySize: 50MB
    rateLimit:
      requests: 50
      window: PT30M
```

**ISO-8601 Duration Format:**
- `PT30S` = 30 seconds
- `PT5M` = 5 minutes
- `PT1H` = 1 hour
- `PT2H30M` = 2 hours 30 minutes

**Size Format:**
- `10MB` = 10 megabytes
- `1GB` = 1 gigabyte
- `512KB` = 512 kilobytes
- `1048576` = bytes (no suffix)


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

1. **Develop APIs**: Create REST controllers or GraphQL resolvers
2. **Discover**: Visit admin UI at `/mcp/admin/tools` to see discovered endpoints
3. **Export**: Download YAML template with all discovered tools
4. **Approve**: Edit `approved-tools.yml` to approve safe tools for AI agents
5. **Reload**: POST to `/mcp/admin/tools/reload` or restart application
```bash
# Reload approved tools without restart
curl -X POST http://localhost:8080/mcp/admin/tools/reload
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
         │
         ▼
┌─────────────────┐
│ Check Approval  │
│ (YAML config)   │
└────────┬────────┘
         │
         ▼
      ✅ / ❌
```

## Architecture

```
┌─────────────────────────────────────────────────┐
│              Spring Boot Application            │
│  ┌───────────────────────────────────────────┐  │
│  │         Auto-Configuration                │  │
│  │  - Discovers REST endpoints               │  │
│  │  - Discovers GraphQL endpoints            │  │
│  │  - Parses OpenAPI specifications          │  │
│  │  - Registers MCP endpoints                │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  ┌───────────────┐       ┌───────────────-───┐  │
│  │  Tool         │       │  Tool Approval    │  │
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
│  │  - HTTP tool execution                    │  │
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
│  Endpoint        │
│  Discovered      │
│  (REST/GraphQL)  │
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
- [x] Rate limiting and execution timeouts
- [ ] WebSocket transport support
- [ ] Resource and Prompt support
- [ ] Enhanced metrics and monitoring
- [ ] Audit logging
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

**UserController.java:**
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping
    public List<User> getAllUsers() {
        return List.of(
            new User(1L, "Alice"),
            new User(2L, "Bob")
        );
    }
}
```

**approved-tools.yml:**
```yaml
approvedTools:
  - get_all_users
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

See [TODO.md](TODO.md) for the complete execution plan and roadmap.

**Current Focus (v1.0.0):**
- ✅ REST/GraphQL endpoint auto-discovery
- ✅ HTTP-based tool execution
- ✅ YAML-based security approval
- ✅ SSE transport
- ✅ Admin UI

**High Priority (v1.1.0+):**
- [ ] Enhanced security features (rate limiting, timeouts, audit logging)
- [ ] WebSocket transport support
- [ ] Metrics and monitoring
- [ ] Performance optimizations

**Future Considerations:**
- [ ] MCP Resources and Prompts support
- [ ] Multi-tenant support
- [ ] Extended documentation and examples

---

**Made with ❤️ for the AI community**

# Auto MCP Server for Spring Boot 3

[![Maven Central](https://img.shields.io/maven-central/v/com.girisenji.ai/spring-boot-mcp-server)](https://central.sonatype.com/artifact/com.girisenji.ai/spring-boot-mcp-server)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-green)](https://spring.io/projects/spring-boot)

A Spring Boot 3 starter library that automatically transforms any JDK 21+ microservice into an **MCP (Model Context Protocol) server**, enabling AI agents to interact with your REST and GraphQL APIs as tools.

## 🚀 Features

### Automatic API Discovery
- **OpenAPI/Swagger Detection**: Automatically discovers and parses OpenAPI 3.x specifications
- **REST Endpoint Scanning**: Falls back to scanning `@RestController` and `@RequestMapping` annotations
- **GraphQL Support**: Detects and exposes GraphQL schemas and queries as MCP tools
- **Smart Introspection**: Uses Spring's application context for comprehensive endpoint discovery

### MCP Server Capabilities
- **SSE Transport**: Implements MCP protocol over Server-Sent Events
- **Tool Registry**: Converts each API endpoint into an MCP tool with proper JSON Schema
- **Dynamic Discovery**: Real-time tool discovery and updates
- **Standard Compliance**: Follows the official Model Context Protocol specification

### Zero Configuration
- **Auto-Configuration**: Works out-of-the-box with Spring Boot starter
- **Smart Defaults**: Sensible defaults with full customization options
- **Security Aware**: Respects existing Spring Security configurations
- **Flexible Filtering**: Include/exclude endpoints via Ant-style patterns

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

### 1. Add the Dependency

Simply add the starter to your existing Spring Boot 3 application.

### 2. That's It!

The library automatically:
1. Scans your application for REST/GraphQL endpoints
2. Generates MCP tool definitions with JSON schemas
3. Exposes the `/mcp` endpoint for AI agent connections

### 3. Connect an AI Agent

```bash
# Health check
curl http://localhost:8080/mcp/health

# List available tools
curl -X POST http://localhost:8080/mcp/messages \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'
```

## ⚙️ Configuration

### Basic Configuration

```yaml
auto-mcp-server:
  enabled: true              # Enable/disable the MCP server
  endpoint: /mcp            # MCP endpoint path
  eager-init: true          # Initialize tool registry on startup
```

### Tool Approval (Security & Governance)

> **📘 Important**: This is a Spring Boot **starter library**. Configuration including `approved-tools.yml` goes in **your application**, NOT in the library JAR itself.

Control which APIs are exposed to AI agents:

```yaml
auto-mcp-server:
  tools:
    approval-mode: config-based   # ✅ RECOMMENDED FOR PRODUCTION
    
    # Path to approved tools config file in YOUR application
    approval-config-file: classpath:approved-tools.yml
    
    # Optional: Auto-approve patterns (for manual mode during development)
    auto-approve-patterns:
      - "get_api_public_*"
      - "query_*"
```

**Approval Modes:**
- **✅ `config-based`** - Only tools in config file are approved _(RECOMMENDED FOR PRODUCTION - persistent, versioned in Git)_
- **⚠️ `manual`** - Tools require explicit approval via management API _(DEVELOPMENT ONLY - approvals lost on restart)_
- **⚠️ `auto`** - All discovered tools are automatically approved _(DEVELOPMENT ONLY - security risk)_

**Where to put `approved-tools.yml`?**
- ✅ In **your application's** `src/main/resources/approved-tools.yml`
- ❌ NOT in the library JAR

> **⚠️ Critical**: `manual` mode stores approvals **in-memory only**. All approvals are **LOST on restart**. Use `config-based` mode with `approved-tools.yml` for production (approvals persisted, versioned in Git).

### Discovery Configuration

```yaml
auto-mcp-server:
  discovery:
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

### Approval Workflow Example

```bash
# 1. Check what tools were discovered
curl http://localhost:8080/mcp/admin/tools/summary

# 2. Review pending tools
curl http://localhost:8080/mcp/admin/tools/pending

# 3. Approve safe endpoints
curl -X POST http://localhost:8080/mcp/admin/tools/get_api_users/approve \
  -H "Content-Type: application/json" \
  -d '{"approvedBy": "admin@example.com"}'

# 4. Bulk approve by pattern
curl -X POST http://localhost:8080/mcp/admin/tools/approve-pattern \
  -H "Content-Type: application/json" \
  -d '{
    "pattern": "get_api_public_*",
    "approvedBy": "admin@example.com"
  }'

# 5. Reject dangerous endpoints
curl -X POST http://localhost:8080/mcp/admin/tools/delete_all_users/reject \
  -H "Content-Type: application/json" \
  -d '{
    "rejectedBy": "security@example.com",
    "reason": "Destructive operation"
  }'
```

## 🔧 Advanced Usage

### Custom Tool Discovery

You can implement your own discovery service:

```java
@Component
public class CustomDiscoveryService implements EndpointDiscoveryService {
    
    @Override
    public List<McpProtocol.Tool> discoverTools() {
        // Your custom discovery logic
        return tools;
    }
    
    @Override
    public String getDiscoveryType() {
        return "Custom";
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

### Programmatic Access

```java
@Autowired
private McpToolRegistry toolRegistry;

public void listTools() {
    List<McpProtocol.Tool> tools = toolRegistry.getAllTools();
    tools.forEach(tool -> 
        System.out.println(tool.name() + ": " + tool.description())
    );
}
```

### Refresh Tools at Runtime

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
         │                      ├─ In YAML? ──▶ Approved
         │                      └─ Not in YAML ──▶ Rejected
         │
         ├──────────────▶ MANUAL Mode (⚠️ DEV ONLY)
         │               ┌──────────────┐
         │               │Match Pattern?│
         │               └──────┬───────┘
         │                      ├─ Yes ──▶ Auto-Approved (in-memory)
         │                      └─ No ───▶ Pending → Manual Review
         │                              ⚠️ LOST ON RESTART
         │
         └──────────────▶ AUTO Mode (⚠️ DEV ONLY)
                                └─────▶ All Approved (security risk)

┌─────────────────┐
│ Only APPROVED   │──▶ Exposed to AI Agents via /mcp
│ Tools Exposed   │
└─────────────────┘
```

### Tool Naming

Tools are named using the following strategy:
1. Use `operationId` from OpenAPI if available
2. Generate from HTTP method + path (e.g., `get_users`, `post_orders`)
3. Sanitize to alphanumeric + underscores
4. Truncate to max length (default: 100 chars)

### MCP Protocol

The library implements the [Model Context Protocol](https://modelcontextprotocol.io):

#### Supported Methods
- `initialize` - Initialize the MCP session
- `tools/list` - List all available tools
- `tools/call` - Execute a tool
- `x] Tool approval workflow
- [x] Management API for tool governance
- [ping` - Health check

#### Transport
- Server-Sent Events (SSE) for streaming
- JSON-RPC 2.0 for request/response

## 🧪 Example Project

See the [example](example/) directory for a complete working example.

Here's a simple REST API example:

```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping
    public List<User> getUsers() {
        return userService.findAll();
    }
    
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.create(user);
    }
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
```

After adding the starter dependency, these endpoints are automatically exposed as MCP tools:
- `get_api_users` - Get all users
- `post_api_users` - Create a new user
- `get_api_users` - Get a specific user

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

# Example Spring Boot Application with Auto MCP Server

This directory contains a sample application demonstrating how to use the Auto MCP Server library.

## Structure

```
example/
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java          # Main application
│   ├── controller/
│   │   ├── UserController.java       # Sample REST controller
│   │   └── ProductController.java    # Another REST controller
│   ├── model/
│   │   ├── User.java                 # User model
│   │   └── Product.java              # Product model
│   └── service/
│       ├── UserService.java          # User service
│       └── ProductService.java       # Product service
├── src/main/resources/
│   └── application.yml                # Configuration
└── pom.xml                            # Maven POM
```

## Quick Start

1. Build and run:
```bash
cd example
mvn spring-boot:run
```

2. Check health:
```bash
curl http://localhost:8080/mcp/health
```

3. Check tool approval summary:
```bash
curl http://localhost:8080/mcp/admin/tools/summary
```

4. List approved tools:
```bash
curl http://localhost:8080/mcp/admin/tools/approved
```

5. List MCP tools (will only show approved tools):
```bash
curl -X POST http://localhost:8080/mcp/messages \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'
```

## Expected Tools

The example uses `config-based` approval mode (see [application.yml](src/main/resources/application.yml)).

**Approved (defined in approved-tools.yml):**
- `get_api_users` - Get all users
- `get_api_products` - Get all products

**Not in approved-tools.yml (will be rejected):**
- `post_api_users` - Create a user
- `get_api_users_id` - Get user by ID
- `delete_api_users_id` - Delete user by ID
- `post_api_products` - Create a product
- `get_api_products_id` - Get product by ID

> **Note**: To approve additional tools, add them to `approved-tools.yml` and restart the application. Alternatively, switch to `manual` mode for development/discovery workflow (see Configuration below).

### Development Workflow: Discover and Approve Tools

If you want to use the management API to discover and approve tools:

1. Switch to `manual` mode in `application.yml`:
```yaml
auto-mcp-server:
  tools:
    approval-mode: manual  # ⚠️ DEV ONLY - approvals lost on restart
```

2. Restart the application and approve tools via API:

```bash
# Approve a specific tool
curl -X POST http://localhost:8080/mcp/admin/tools/post_api_users/approve \
  -H "Content-Type: application/json" \
  -d '{"approvedBy": "developer@example.com"}'

# Bulk approve all GET operations
curl -X POST http://localhost:8080/mcp/admin/tools/approve-pattern \
  -H "Content-Type: application/json" \
  -d '{
    "pattern": "get_*",
    "approvedBy": "developer@example.com"
  }'
```

3. Export approved tools to YAML (TODO: export endpoint)
4. Switch back to `config-based` mode for production

## Configuration

See [application.yml](src/main/resources/application.yml) for MCP server configuration options.

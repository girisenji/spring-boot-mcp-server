# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-02-11

### Added

- Initial release of Spring Boot MCP Server
- Auto-configuration for MCP server in Spring Boot 3.2+ applications
- SSE (Server-Sent Events) transport implementation for MCP protocol
- `McpTool` interface for creating custom tools
- Tool discovery via Spring component scanning
- Config-driven tool approval system via `approved-tools.yml`
- `ToolApprovalService` for managing approved tools
- `McpToolRegistry` for tool registration and filtering
- Admin UI at `/mcp/admin/tools` for tool management
- REST API for tool management:
  - `GET /mcp/admin/tools/api/tools` - List tools with pagination
  - `POST /mcp/admin/tools/reload` - Reload approved tools
  - `GET /mcp/admin/tools/export/yaml` - Export tools as YAML
- MCP protocol endpoints:
  - `GET /mcp/sse` - Main SSE endpoint for AI agent connections
- Runtime configuration reload support
- Comprehensive documentation (README, guides, examples)
- Example application demonstrating usage
- JUnit and Mockito test coverage
- Java 21 support
- Spring Boot 3.2+ support

### Security

- Deny-by-default tool approval
- Explicit approval required via YAML configuration
- No auto-approval mechanisms
- Config files version-controlled for audit trail

[Unreleased]: https://github.com/girisenji/spring-boot-mcp-server/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/girisenji/spring-boot-mcp-server/releases/tag/v1.0.0

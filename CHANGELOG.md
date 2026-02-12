# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Rate Limiting**: Per-tool and per-client IP rate limiting with Caffeine cache
  - Configurable limits via `approved-tools.yml` (ISO-8601 duration format: PT1H, PT30M, etc.)
  - Default limit: 100 requests/hour (configurable via `auto-mcp-server.rate-limiting.default-requests-per-hour`)
  - Client IP tracking via X-Forwarded-For, X-Real-IP, and RemoteAddr headers
  - Rate limiting can be enabled/disabled via `auto-mcp-server.rate-limiting.enabled` property
  - Comprehensive error messages when rate limit is exceeded
- **RateLimitService**: Service for managing and enforcing rate limits
  - In-memory Caffeine cache with 2-hour TTL and 100K max entries
  - Per-tool and per-client-IP tracking
  - Reset functionality for admin operations
  - Statistics endpoint for monitoring cache performance
- **ToolConfigurationService**: Renamed from ToolApprovalService to better reflect functionality
  - Manages tool allowed-list and rate limit configurations from YAML
  - Supports both simple tool format and object format with custom rate limits
  - Terminology updated: "approval" → "allowed-list" throughout codebase

### Changed
- **Service Naming**: `ToolApprovalService` → `ToolConfigurationService`
  - Reflects actual behavior: manages configuration (allowed-list + rate limits), not dynamic approval
  - Updated all references across controllers, registries, tests, and documentation
- **McpToolExecutor**: Now enforces rate limiting when enabled
  - Added `rateLimitingEnabled` constructor parameter
  - Rate limiting check before HTTP execution
  - Human-readable duration formatting in error messages
- **RateLimitConfig**: Enhanced validation with better error messages
  - Parse method validates ISO-8601 duration format
  - Clear exceptions for invalid window formats

### Fixed
- JavaDoc coverage for all public classes, records, and methods
- Added constants for magic strings (header names, default values)
- Improved error handling and validation throughout rate limiting system

### Testing
- Added **15 new tests** for rate limiting functionality
  - 8 tests in `RateLimitServiceTest` (rate limit enforcement, per-client/per-tool tracking, status, reset, defaults)
  - 5 tests in `McpToolExecutorIntegrationTest` (enabled/disabled enforcement, error messages, metadata handling)
  - 10 tests in `ToolConfigurationServiceTest` (YAML loading, rate limits, reload, validation)
- **Total: 61 tests, 0 failures** ✅
- **Code coverage**: Comprehensive test coverage for all new rate limiting code

## [1.0.0] - 2026-02-11

### Added

- Initial release of Spring Boot MCP Server
- Auto-configuration for MCP server in Spring Boot 3.2+ applications
- SSE (Server-Sent Events) transport implementation for MCP protocol
- **REST endpoint auto-discovery** via reflection and OpenAPI parsing
- **GraphQL endpoint auto-discovery** via schema introspection
- **OpenAPI integration** for generating tool schemas from Swagger specs
- Config-driven tool approval system via `approved-tools.yml`
- `ToolApprovalService` for managing approved tools
- `McpToolRegistry` for tool registration and filtering
- Admin UI at `/mcp/admin/tools` for tool management
- **HTTP-based tool execution** - forwards tool calls to REST/GraphQL endpoints
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

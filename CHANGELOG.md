# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Management REST API**: Configuration reload endpoint for zero-downtime updates
  - `POST /mcp/admin/tools/reload` - Reload approved-tools.yml without restart
  - Audit logging for reload operations with before/after counts
  - Comprehensive tests for ToolManagementController (10 tests)
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
- **Execution Timeouts**: Configurable HTTP request timeouts to prevent indefinite hangs
  - Default read timeout: PT30S (30 seconds), configurable via `auto-mcp-server.execution.default-timeout`
  - Default connect timeout: PT5S (5 seconds), configurable via `auto-mcp-server.execution.default-connect-timeout`
  - Per-tool timeout overrides via `approved-tools.yml` (ISO-8601 duration format)
  - RestTemplate factory with timeout configuration
  - Graceful timeout handling with clear error messages (shows configured timeout)
  - Separate read and connect timeout configuration
- **Request Size Limits**: Protection against memory exhaustion from large payloads
  - Default request body limit: 10MB, configurable via `auto-mcp-server.execution.max-request-body-size`
  - Default response body limit: 10MB, configurable via `auto-mcp-server.execution.max-response-body-size`
  - Human-readable size format (10MB, 1GB, 512KB, bytes)
  - Per-tool size limit overrides via `approved-tools.yml`
  - Long type support (up to exabyte scale)
  - User-friendly error messages showing actual vs allowed sizes
  - Request validation before HTTP execution, response validation after
- **Audit Logging**: Comprehensive structured logging for security and compliance
  - Event types: TOOL_EXECUTION, APPROVAL_CHANGE, RATE_LIMIT_EXCEEDED, EXECUTION_TIMEOUT, SIZE_LIMIT_EXCEEDED
  - Log formats: PLAIN (human-readable key=value) and JSON (SIEM-compatible)
  - Automatic sensitive data redaction for password, token, apiKey, secret, auth fields (case-insensitive, nested)
  - Default configuration: Enabled with PLAIN format, all event types logged
  - Per-event-type toggles via `auto-mcp-server.audit.*` properties
  - Integration: Spring Boot logging framework (INFO for success, WARN for failures/security events)

### Changed
- **Documentation**: README.md updated to reflect actual Management REST API endpoints
  - Removed non-existent runtime approval endpoints (approve, reject, approve-pattern)
  - Clarified API is for discovery and YAML generation, not dynamic approval
  - Added reload endpoint documentation with examples
- **Service Naming**: `ToolApprovalService` → `ToolConfigurationService`
  - Reflects actual behavior: manages configuration (allowed-list + rate limits), not dynamic approval
  - Updated all references across controllers, registries, tests, and documentation
- **McpToolExecutor**: Now enforces rate limiting when enabled
  - Added `rateLimitingEnabled` constructor parameter
  - Rate limiting check before HTTP execution
  - Human-readable duration formatting in error messages
  - Timeout-aware RestTemplate creation per execution
  - Request and response size validation
- **AutoMcpServerProperties**: Added `Execution`, `RateLimiting`, and `Audit` configuration records
- **ExecutionTimeout**: New model for timeout configuration
  - Validates ISO-8601 duration format
  - Converts to milliseconds for HTTP client configuration
  - Validates positive, non-zero durations
- **SizeLimit**: New model for request/response size limits
  - Parses human-readable sizes (10MB, 1GB, 512KB)
  - Case-insensitive, handles whitespace
  - Null-safe defaults
- **AuditLogger**: Service for structured audit logging
  - Formats: PLAIN (human-readable) and JSON (SIEM-compatible)
  - Automatic sensitive data sanitization (password, token, apiKey, secret, auth - case-insensitive, nested)
  - Event types: TOOL_EXECUTION, APPROVAL_CHANGE, RATE_LIMIT_EXCEEDED, EXECUTION_TIMEOUT, SIZE_LIMIT_EXCEEDED
  - Configuration-driven event type filtering
- **AuditEvent**: Immutable record model for audit events
  - Factory methods for each event type
  - Timestamp, client IP, success flag, error message, metadata

### Testing
- Added **73 new tests** for security and reliability features
  - **Rate Limiting** (15 tests):
    - 8 tests in `RateLimitServiceTest` (enforcement, per-client/per-tool tracking, status, reset, defaults)
    - 5 tests in `McpToolExecutorIntegrationTest` (enabled/disabled enforcement, error messages)
    - 2 tests in `ToolConfigurationServiceTest` (YAML loading, reload)
  - **Execution Timeouts** (15 tests):
    - 15 tests in `ExecutionTimeoutTest` (ISO-8601 parsing, validation, millisecond conversion)
    - Coverage: valid/invalid formats, short/long/complex durations, null/blank/negative/zero rejection
  - **Request Size Limits** (26 tests):
    - 26 tests in `SizeLimitTest` (size parsing, validation, formatting)
    - Coverage: MB/GB/KB/bytes parsing, case-insensitivity, whitespace, null defaults, byte formatting
  - **Audit Logging** (17 tests):
    - 17 tests in `AuditLoggerTest` (PLAIN/JSON formats, argument sanitization, security events)
    - Coverage: enabled/disabled, format tests, sensitive field redaction, all event types, log levels
- **Total: 119 tests, 0 failures** ✅
- **Code coverage**: Comprehensive test coverage for all new security and reliability features

### Fixed
- JavaDoc coverage for all public classes, records, and methods
- Added constants for magic strings (header names, default values)
- Improved error handling and validation throughout rate limiting system
- Fixed sensitive field sanitization to be properly case-insensitive
- Added JavaTimeModule to ObjectMapper test instances for Instant serialization

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
- Tool Management API at `/mcp/admin/tools` for tool discovery and configuration
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

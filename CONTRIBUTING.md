# Contributing to Auto MCP Server

Thank you for your interest in contributing to Auto MCP Server! We welcome contributions from the community.

## Development Setup

### Prerequisites

- **Java 21** or higher
- **Maven 3.8+**
- **Git**
- An IDE (IntelliJ IDEA, VS Code, Eclipse, etc.)

### Getting Started

1. **Fork the repository**
   ```bash
   git clone https://github.com/girisenji/spring-boot-mcp-server.git
   cd auto-mcp-server
   ```

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Run tests**
   ```bash
   mvn test
   ```

4. **Run the example application**
   ```bash
   cd example
   mvn spring-boot:run
   ```

## Code Style

- Follow standard Java coding conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs
- Keep methods focused and concise
- Use Java 21 features appropriately (records, pattern matching, etc.)

## Making Changes

1. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Write clean, well-documented code
   - Add unit tests for new functionality
   - Update documentation as needed

3. **Test your changes**
   ```bash
   mvn clean test
   ```

4. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```

   Follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` - New feature
   - `fix:` - Bug fix
   - `docs:` - Documentation changes
   - `test:` - Test additions or modifications
   - `refactor:` - Code refactoring
   - `chore:` - Maintenance tasks

5. **Push and create a pull request**
   ```bash
   git push origin feature/your-feature-name
   ```

## Pull Request Guidelines

- Provide a clear description of the changes
- Reference any related issues
- Ensure all tests pass
- Update documentation if needed
- Keep PRs focused on a single feature or fix
- Respond to review feedback promptly

## Testing

We use JUnit 5 and Mockito for testing. Please ensure:

- All new code has appropriate test coverage
- Tests are isolated and don't depend on external resources
- Tests are descriptive and well-named
- Both positive and negative test cases are covered

Example test structure:
```java
@Test
void testFeatureName_whenCondition_thenExpectedOutcome() {
    // Given
    // Setup test data and mocks
    
    // When
    // Execute the code under test
    
    // Then
    // Assert expected outcomes
}
```

## Documentation

- Update README.md for user-facing changes
- Add JavaDoc for all public APIs
- Include code examples where helpful
- Update configuration documentation

## Areas for Contribution

We welcome contributions in these areas:

### High Priority
- [ ] Implement tool execution functionality
- [ ] Add WebSocket transport support
- [ ] Enhance security features
- [ ] Add metrics and monitoring
- [ ] Performance optimizations

### Medium Priority
- [ ] Support for MCP Resources
- [ ] Support for MCP Prompts
- [ ] Enhanced error handling
- [ ] Rate limiting and throttling
- [ ] Caching mechanisms

### Documentation
- [ ] More example applications
- [ ] Integration guides
- [ ] Video tutorials
- [ ] Best practices guide

### Testing
- [ ] Integration tests
- [ ] Performance tests
- [ ] Security tests
- [ ] Example test scenarios

## Questions or Issues?

- Open an issue for bugs or feature requests
- Use discussions for questions
- Tag issues appropriately

## Code of Conduct

- Be respectful and inclusive
- Provide constructive feedback
- Focus on the best solution, not personal preferences
- Help others learn and grow

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

---

Thank you for contributing to Auto MCP Server! 🎉

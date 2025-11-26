# Contributing to Camel Integration Capability

Thank you for your interest in contributing to the Camel Integration Capability! This document provides guidelines and information for contributors.

## Code of Conduct

We expect all contributors to be respectful and professional. Create a welcoming environment for everyone regardless of experience level, background, or identity.

## Getting Started

### Prerequisites

- **Java 21 or higher** - Required for compilation and runtime
- **Apache Maven 3.9+** - Build tool and dependency management
- **Git** - Version control
- **IDE** (recommended) - IntelliJ IDEA, Eclipse, or VS Code with Java extensions

### Development Environment Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/wanaku-ai/camel-integration-capability.git
   cd camel-integration-capability
   ```

2. **Build the project**:
   ```bash
   mvn clean install
   ```

3. **Run tests**:
   ```bash
   mvn test
   ```

4. **Run the application locally**:
   ```bash
   java -jar target/camel-integration-capability-0.0.9-SNAPSHOT-jar-with-dependencies.jar \
     --registration-url http://localhost:8080 \
     --registration-announce-address localhost \
     --routes-ref file:///path/to/example-routes.camel.yaml \
     --rules-ref file:///path/to/example-rules.yaml \
     --client-id wanaku-service \
     --client-secret test-secret
   ```

> [!NOTE]
> You'll need a running Wanaku stack (Wanaku MCP Router, OAuth2 provider) for full integration testing. See the [Usage Guide](docs/usage.md) for setup instructions.

## Project Structure

```
camel-integration-capability/
├── src/
│   ├── main/
│   │   ├── java/ai/wanaku/capability/camel/
│   │   │   ├── CamelToolMain.java          # Application entry point
│   │   │   ├── WanakuCamelManager.java     # Camel context management
│   │   │   ├── grpc/                       # gRPC service implementations
│   │   │   │   ├── CamelTool.java          # Tool invocation service
│   │   │   │   ├── CamelResource.java      # Resource retrieval service
│   │   │   │   └── ProvisionBase.java      # Capability metadata service
│   │   │   ├── model/                      # Data models
│   │   │   ├── spec/                       # MCP specification handling
│   │   │   │   └── rules/                  # Route exposure rules
│   │   │   ├── util/                       # Utilities
│   │   │   │   └── WanakuRoutesLoader.java # Route loading logic
│   │   │   ├── downloader/                 # Resource downloaders
│   │   │   └── init/                       # Initialization logic
│   │   └── resources/
│   │       └── log4j2.xml                  # Logging configuration
│   └── test/
│       └── java/                           # Unit and integration tests
├── docs/                                   # Documentation
├── pom.xml                                 # Maven build configuration
├── Dockerfile                              # Container build definition
└── README.md                               # Project overview
```

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

Use descriptive branch names:
- `feature/add-http-basic-auth` - New features
- `fix/route-loading-error` - Bug fixes
- `docs/improve-readme` - Documentation updates
- `refactor/simplify-downloader` - Code refactoring

### 2. Make Your Changes

- Write clean, readable code following existing style
- Add tests for new functionality
- Update documentation as needed
- Keep commits focused and atomic

### 3. Test Your Changes

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=YourTestClass

# Run with coverage (if configured)
mvn verify
```

### 4. Commit Your Changes

Write clear, descriptive commit messages:

```bash
git commit -m "Add support for HTTP Basic authentication

- Implement BasicAuthHandler in downloader package
- Add username/password CLI parameters
- Update documentation with auth examples
- Add unit tests for BasicAuthHandler

This resolves issue #<the issue number goes here>
"
```

**Commit message format**:
- **First line**: Brief summary (50 chars or less)
- **Blank line**
- **Body**: Detailed explanation of changes (wrap at 72 chars)
  - What changed
  - Why it changed
  - Any breaking changes or migration notes
  - Add the issue number

### 5. Push and Create Pull Request

```bash
git push origin feature/your-feature-name
```

Create a pull request on GitHub with:
- **Clear title** describing the change
- **Description** explaining what and why
- **References** to related issues (e.g., "Fixes #123")
- **Testing notes** for reviewers

## Coding Standards

### Java Code Style

- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters maximum
- **Naming conventions**:
  - Classes: `PascalCase`
  - Methods/variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: lowercase, no underscores
- **Imports**: No wildcard imports (`import java.util.*`)
- **Organization**: Group imports (java.*, javax.*, org.*, ai.wanaku.*)

### Code Quality

- **Single Responsibility**: Each class/method should have one clear purpose
- **DRY Principle**: Don't repeat yourself - extract common code
- **Error Handling**: Use appropriate exception types, include context in messages
- **Logging**:
  - Use SLF4J logger: `private static final Logger LOG = LoggerFactory.getLogger(ClassName.class);`
  - Log levels:
    - `ERROR`: Failures requiring immediate attention
    - `WARN`: Degraded functionality, recoverable errors
    - `INFO`: Important business events (startup, registration, route loading)
    - `DEBUG`: Detailed diagnostic information
    - `TRACE`: Very detailed tracing (rarely used)

### Comments and Documentation

- **JavaDoc**: Required for all public classes and methods
- **Inline comments**: Explain "why", not "what"
- **TODOs**: Use `// TODO: description` for future improvements

Example:

```java
/**
 * Downloads resources from the Wanaku DataStore service using OAuth2 authentication.
 *
 * @param uri the DataStore URI (e.g., "datastore://routes.yaml")
 * @param targetPath the local path where the resource should be saved
 * @return the downloaded file path
 * @throws DownloadException if the download fails or authentication is rejected
 */
public Path downloadFromDataStore(URI uri, Path targetPath) throws DownloadException {
    // Implementation
}
```

## Testing Guidelines

### Unit Tests

- **Coverage**: Aim for 80%+ code coverage on new code
- **Naming**: `testMethodName_scenario_expectedResult`
- **Structure**: Arrange-Act-Assert pattern
- **Isolation**: Use mocks for external dependencies

Example:

```java
@Test
void loadRoutes_withValidYaml_shouldLoadSuccessfully() {
    // Arrange
    String yaml = """
        - route:
            id: test-route
            from:
              uri: direct:test
        """;

    // Act
    List<Route> routes = loader.loadRoutes(yaml);

    // Assert
    assertEquals(1, routes.size());
    assertEquals("test-route", routes.get(0).getId());
}
```

### Integration Tests

- Test interactions between components
- Use testcontainers for external dependencies when possible
- Mark with `@Tag("integration")` for selective execution

## Pull Request Process

1. **Ensure all tests pass**: `mvn clean verify`
2. **Update documentation**: README, docs/, JavaDoc, CHANGELOG
3. **Rebase on main**: Keep commit history clean
4. **Request review**: At least one maintainer approval required
5. **Address feedback**: Make requested changes promptly
6. **Squash commits**: If requested by maintainers
7. **Merge**: Maintainers will merge once approved

### Pull Request Checklist

- [ ] Tests added/updated and passing
- [ ] Documentation updated
- [ ] Code follows style guidelines
- [ ] Commit messages are clear
- [ ] No merge conflicts with main branch
- [ ] CHANGELOG.md updated (if applicable)

## Building and Packaging

### Local Build

```bash
# Compile only
mvn clean compile

# Run tests
mvn test

# Package JAR
mvn package

# Install to local Maven repo
mvn install
```

### Docker Build

```bash
# Build container image
docker build -t camel-capability:dev .

# Run container locally
docker run -p 9190:9190 camel-capability:dev [options]
```

### Release Build

Releases are automated via GitHub Actions. See [Release Guide](docs/release-guide.md).

## Reporting Issues

### Bug Reports

When reporting bugs, include:

1. **Description**: Clear summary of the issue
2. **Steps to reproduce**: Exact steps to trigger the bug
3. **Expected behavior**: What should happen
4. **Actual behavior**: What actually happens
5. **Environment**:
   - Java version (`java -version`)
   - Maven version (`mvn -v`)
   - OS and version
   - Relevant configuration (routes, rules, CLI parameters)
6. **Logs**: Relevant log output (use DEBUG level if possible)
7. **Stack traces**: Full exception stack traces

### Feature Requests

When requesting features, include:

1. **Problem statement**: What problem does this solve?
2. **Proposed solution**: How should it work?
3. **Alternatives considered**: Other approaches you've thought about
4. **Use case**: Real-world scenario where this is needed

## Security Vulnerabilities

**Do not** report security vulnerabilities via public GitHub issues. See [SECURITY.md](SECURITY.md) for reporting process.

## Communication

- **GitHub Issues**: Bug reports, feature requests, questions
- **Pull Requests**: Code contributions, documentation improvements
- **Email**: contact@wanaku.ai for general inquiries

## Recognition

Contributors will be recognized in:
- GitHub contributor list
- Release notes for significant contributions
- Project documentation where appropriate

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0, the same license as the project. See [LICENSE](LICENSE) for details.

---

**Questions?** Open an issue or reach out to contact@wanaku.ai. We're here to help!

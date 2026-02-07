# Implementation Plan: Multi-Module Project Split

**Branch**: `001-multi-module-split` | **Date**: 2026-02-07 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-multi-module-split/spec.md`

## Summary

Restructure the project from a single-module Maven project into a multi-module hierarchy with three modules:
- **Common**: Reusable business logic, utilities, and shared components
- **Plugin**: ContextServicePlugin-based SPI plugin for Camel integration
- **Main**: Standalone CLI application (current code)

The plugin module enables embedding the Camel integration capability into existing Camel applications via SPI discovery, configured through properties files and environment variables (critical for OpenShift deployments).

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Apache Camel 4.14.4, Wanaku SDK 0.1.0-SNAPSHOT, picocli 4.7.7, gRPC
**Storage**: N/A (file-based configuration)
**Testing**: JUnit 5.14.2, Maven Failsafe for integration tests
**Target Platform**: Linux server, OpenShift/Kubernetes containers
**Project Type**: Multi-module Maven project
**Performance Goals**: Route execution <200ms p95, startup <30s, 100 concurrent gRPC requests
**Constraints**: <512MB heap, no blocking on event loop, environment variable configuration
**Scale/Scope**: 41 Java files to reorganize, 3 new modules, CI/CD updates, documentation

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | ✅ Pass | No new code patterns; structural refactoring preserves existing style |
| II. Testing Standards | ✅ Pass | All existing tests must pass (FR-009); new plugin requires unit tests |
| III. User Experience | ✅ Pass | CLI interface preserved (FR-008); plugin adds new deployment option |
| IV. Performance | ✅ Pass | No performance changes; same code paths |

**Quality Gates Compliance**:
- Compilation: Zero errors/warnings required
- Static Analysis: Spotless formatting enforced
- Unit Tests: ≥80% coverage for new plugin code
- Integration Tests: Existing tests must pass
- Documentation: Usage guide and plugin docs required

## Project Structure

### Documentation (this feature)

```text
specs/001-multi-module-split/
├── plan.md              # This file
├── research.md          # Research decisions (Phase 0)
├── data-model.md        # Module structure details (Phase 1)
├── quickstart.md        # Development setup guide (Phase 1)
├── contracts/           # Interface contracts (Phase 1)
│   ├── plugin-spi.md
│   └── module-dependencies.md
└── tasks.md             # Implementation tasks (Phase 2 - /speckit.tasks)
```

### Source Code (repository root)

```text
/pom.xml                                    # Parent aggregator (packaging: pom)
│
├── camel-integration-capability-common/    # Common module
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/ai/wanaku/capability/camel/
│       │   │   ├── WanakuCamelManager.java
│       │   │   ├── downloader/
│       │   │   ├── grpc/
│       │   │   ├── init/
│       │   │   ├── model/
│       │   │   ├── spec/
│       │   │   └── util/
│       │   └── resources/
│       │       └── cic-version.txt
│       └── test/java/
│
├── camel-integration-capability-runtimes/                               # Runtimes aggregator
│   ├── pom.xml
│   │
│   ├── camel-integration-capability-plugin/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/ai/wanaku/capability/camel/plugin/
│   │       │   │   ├── CamelIntegrationPlugin.java
│   │       │   │   └── PluginConfiguration.java
│   │       │   └── resources/
│   │       │       ├── META-INF/services/org.apache.camel.spi.ContextServicePlugin
│   │       │       └── camel-integration-capability.properties
│   │       └── test/java/
│   │
│   └── camel-integration-capability-main/
│       ├── pom.xml
│       └── src/
│           ├── main/
│           │   ├── java/ai/wanaku/capability/camel/
│           │   │   └── CamelToolMain.java
│           │   └── resources/
│           │       └── log4j2.properties
│           └── test/java/

├── docs/
│   ├── usage.md                            # Updated with plugin reference
│   └── plugin-usage.md                     # New: plugin documentation

├── .github/workflows/
│   ├── main-build.yml                      # Updated jar paths
│   └── ...

└── Dockerfile                              # Updated jar path
```

**Structure Decision**: Multi-module Maven project with nested runtimes aggregator. This structure groups deployment artifacts (plugin, main) under `camel-integration-capability-runtimes/` while keeping the reusable library at root level for visibility.

## Key Implementation Decisions

### 1. Plugin Configuration

The plugin cannot receive CLI arguments. Configuration via:
1. Properties file: `camel-integration-capability.properties` in classpath
2. Environment variables: Override properties (takes precedence)

See [contracts/plugin-spi.md](./contracts/plugin-spi.md) for full mapping.

### 2. ContextServicePlugin Pattern

Plugin implements `org.apache.camel.spi.ContextServicePlugin`:
- `load(CamelContext)`: Initialize resources, start gRPC, register with discovery
- `unload(CamelContext)`: Cleanup, deregister, stop gRPC

Discovered via `META-INF/services/org.apache.camel.spi.ContextServicePlugin`.

### 3. Code Distribution

| Current Location | Target Module | Rationale |
|-----------------|---------------|-----------|
| `CamelToolMain.java` | main | CLI-specific entry point |
| `model/`, `util/`, `spec/`, `grpc/`, `init/`, `downloader/` | common | Reusable business logic |
| `WanakuCamelManager.java` | common | Core management, used by both |
| New: `CamelIntegrationPlugin.java` | plugin | SPI implementation |
| New: `PluginConfiguration.java` | plugin | Properties/env loader |

### 4. CI/CD Updates

- `Dockerfile`: Update path to `camel-integration-capability-runtimes/camel-integration-capability-main/target/`
- `main-build.yml`: Build from root (reactor handles modules)
- `release-artifacts.yml`: Update artifact paths
- Container registry: Same image from main module

## Complexity Tracking

No constitution violations. This is a structural refactoring that preserves existing behavior.

## Artifacts Generated

| Artifact | Location | Description |
|----------|----------|-------------|
| research.md | specs/001-multi-module-split/ | Research decisions |
| data-model.md | specs/001-multi-module-split/ | Module structure |
| quickstart.md | specs/001-multi-module-split/ | Development guide |
| plugin-spi.md | specs/001-multi-module-split/contracts/ | SPI contract |
| module-dependencies.md | specs/001-multi-module-split/contracts/ | Dependency rules |

## Next Steps

Run `/speckit.tasks` to generate implementation tasks.

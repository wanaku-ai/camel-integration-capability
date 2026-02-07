# Feature Specification: Multi-Module Project Split

**Feature Branch**: `001-multi-module-split`
**Created**: 2026-02-07
**Status**: Draft
**Input**: User description: "Separate the project in multiple parts, so that we can have different deliverables for different use cases (i.e.: a Camel plugin, a normal java app, etc). To do so, we need to separate the reusable/common part from the rest of the code. The project should be separate in 3 new modules: one for the common code, another for the plugin specific part, and another for the java based app (the current code)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Library Consumer Integrates Common Components (Priority: P1)

A developer wants to use the Camel integration capabilities as a library within their own application or plugin, without inheriting the standalone application dependencies (CLI framework, logging configuration, entry point).

**Why this priority**: This is the core value proposition - enabling reuse of business logic across different deployment contexts.

**Independent Test**: Can be tested by creating a minimal project that imports only the common module and successfully uses its classes without requiring app-specific dependencies.

**Acceptance Scenarios**:

1. **Given** a new project with only the common module dependency, **When** the developer imports utility classes and core logic, **Then** the code compiles and runs without requiring CLI or logging dependencies from the main module.
2. **Given** the common module is published to a repository, **When** a developer adds it as a dependency, **Then** they receive only reusable components without executable artifacts.

---

### User Story 2 - Plugin Developer Creates Camel Plugin (Priority: P1)

A developer wants to create a Camel plugin that integrates with a larger system (e.g., Wanaku ecosystem) using the plugin-specific module.

**Why this priority**: This enables the second primary use case - building plugins that can be loaded into other systems.

**Independent Test**: Can be tested by building a plugin artifact from the plugin module that exposes the necessary integration points without including standalone app code.

**Acceptance Scenarios**:

1. **Given** the plugin module is built, **When** it is deployed as a plugin, **Then** it integrates correctly with the host system without conflicting dependencies.
2. **Given** a plugin consumer imports the plugin module, **When** they use the provided interfaces, **Then** they can invoke Camel routes without needing the standalone app infrastructure.

---

### User Story 3 - Operator Runs Standalone Application (Priority: P2)

An operator wants to run the Camel integration capability as a standalone service, exactly as it works today.

**Why this priority**: Preserves existing functionality; users with current deployment patterns should experience no change.

**Independent Test**: Can be tested by building and running the main module as a standalone executable with the same CLI interface.

**Acceptance Scenarios**:

1. **Given** the main module is built, **When** an operator runs it with current CLI arguments, **Then** the application behaves identically to the pre-split version.
2. **Given** the operator has existing deployment scripts, **When** they update to the new version, **Then** no changes to invocation are required.

---

### Edge Cases

- What happens when a module has a missing dependency on another internal module?
- How does the build handle circular dependencies between modules?
- What happens when someone imports only the plugin module without the common module?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Project MUST be structured as a multi-module build with a parent aggregator
- **FR-002**: Common module MUST contain all reusable business logic, utilities, and shared components
- **FR-003**: Common module MUST NOT contain any executable entry points or CLI-specific code
- **FR-004**: Plugin module MUST depend on the common module
- **FR-005**: Plugin module MUST provide SPI-based interfaces (META-INF/services discovery pattern, based on Apache Camel's ContextServicePlugin) for integration as a loadable component
- **FR-006**: Main module MUST depend on the common module
- **FR-007**: Main module MUST contain the CLI entry point and standalone application infrastructure
- **FR-008**: Main module MUST preserve current command-line interface and behavior
- **FR-009**: All existing tests MUST pass after the restructuring
- **FR-010**: Each module MUST be independently buildable and publishable
- **FR-011**: Plugin module MUST support configuration via properties file and environment variables (environment variables take precedence)
- **FR-012**: Plugin configuration MUST support all parameters currently available as CLI options
- **FR-013**: CI workflows, container automation, and release scripts MUST be updated for multi-module structure
- **FR-014**: Documentation MUST be provided for the plugin module including configuration reference
- **FR-015**: Usage guide MUST be updated to reference the plugin as an alternative deployment option

### Key Entities

- **Common Module** (`camel-integration-capability-common`, root level): Contains shared utilities, model classes, Camel route handling logic, MCP rules processing, and core business logic
- **Plugin Module** (`camel-integration-capability-runtimes/camel-integration-capability-plugin`): Contains SPI-based plugin implementation (META-INF/services), plugin-specific adapters, and minimal wrapper around common functionality; discoverable via standard ServiceLoader pattern
- **Main Module** (`camel-integration-capability-runtimes/camel-integration-capability-main`): Contains CLI entry point, standalone server configuration, logging setup, and deployment-specific resources

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All three modules build successfully independently
- **SC-002**: The standalone application functions identically to pre-split behavior (same CLI interface, same outputs)
- **SC-003**: A consumer can add only the common module as a dependency and use its classes without runtime errors
- **SC-004**: A consumer can add only the plugin module as a dependency and integrate it without requiring app-specific classes
- **SC-005**: 100% of existing tests pass after restructuring
- **SC-006**: Build time increases by no more than 50% compared to single-module build

## Clarifications

### Session 2026-02-07

- Q: What plugin interface/contract should the plugin module expose? → A: SPI-based plugin (META-INF/services discovery, based on Apache Camel's ContextServicePlugin)
- Q: What naming convention for module/artifact names? → A: `camel-integration-capability-common` (root), `camel-integration-capability-runtimes/camel-integration-capability-plugin`, `camel-integration-capability-runtimes/camel-integration-capability-main`
- Q: How should the plugin receive configuration (no CLI)? → A: Properties file + environment variables; env vars take precedence (critical for OpenShift)
- Q: What is the plugin interface? → A: Apache Camel's ContextServicePlugin (see camel-api 4.17.0)

## Assumptions

- The current single-module structure has clear separation points that allow clean extraction
- No breaking changes to public APIs are required; this is a structural refactoring
- Versioning will remain synchronized across all modules (same version number for parent and children)
- The plugin module will expose a subset of functionality suitable for embedding, not the full standalone feature set

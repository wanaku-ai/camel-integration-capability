# Module Dependency Contract

**Feature**: Multi-Module Project Split

## Dependency Graph

```
┌─────────────────────────────────────────────────┐
│           camel-integration-capability-parent    │
│                    (pom aggregator)              │
└─────────────────────────────────────────────────┘
                        │
         ┌──────────────┴──────────────┐
         ▼                             ▼
┌─────────────────────┐    ┌─────────────────────┐
│ camel-integration-  │    │ runtimes            │
│ capability-common   │    │ (pom aggregator)    │
│     (jar)           │    └─────────────────────┘
└─────────────────────┘              │
         ▲                ┌──────────┴──────────┐
         │                ▼                     ▼
         │    ┌─────────────────────┐  ┌─────────────────────┐
         │    │ camel-integration-  │  │ camel-integration-  │
         └────┤ capability-plugin   │  │ capability-main     │
              │     (jar)           │  │     (jar)           │
              └─────────────────────┘  └─────────────────────┘
```

## Common Module Exports

**Artifact**: `ai.wanaku:camel-integration-capability-common`

### Public Packages

| Package | Purpose | Consumers |
|---------|---------|-----------|
| `ai.wanaku.capability.camel` | Core manager class | Plugin, Main |
| `ai.wanaku.capability.camel.model` | Data models | Plugin, Main |
| `ai.wanaku.capability.camel.util` | Utilities | Plugin, Main |
| `ai.wanaku.capability.camel.grpc` | gRPC services | Plugin, Main |
| `ai.wanaku.capability.camel.init` | Initializers | Plugin, Main |
| `ai.wanaku.capability.camel.downloader` | Resource download | Plugin, Main |
| `ai.wanaku.capability.camel.spec.rules` | Rules processing | Plugin, Main |

### Key Classes for Consumers

```java
// Core management
ai.wanaku.capability.camel.WanakuCamelManager

// gRPC services
ai.wanaku.capability.camel.grpc.CamelTool
ai.wanaku.capability.camel.grpc.CamelResource
ai.wanaku.capability.camel.grpc.ProvisionBase

// Initialization
ai.wanaku.capability.camel.init.InitializerFactory
ai.wanaku.capability.camel.init.Initializer

// Resource handling
ai.wanaku.capability.camel.downloader.DownloaderFactory
ai.wanaku.capability.camel.downloader.ResourceDownloaderCallback

// Configuration
ai.wanaku.capability.camel.util.McpRulesManager
```

## Plugin Module Exports

**Artifact**: `ai.wanaku:camel-integration-capability-plugin`

### Public Packages

| Package | Purpose | Consumers |
|---------|---------|-----------|
| `ai.wanaku.capability.camel.plugin` | Plugin implementation | Camel runtime (via SPI) |

### SPI Registration

File: `META-INF/services/org.apache.camel.spi.ContextServicePlugin`

## Main Module Exports

**Artifact**: `ai.wanaku:camel-integration-capability-main`

### Public Packages

None - this is an application module, not a library.

### Executable Artifact

`camel-integration-capability-main-X.Y.Z-jar-with-dependencies.jar`

Entry point: `ai.wanaku.capability.camel.CamelToolMain`

## Dependency Rules

### Allowed

- Common → External libraries only
- Plugin → Common
- Main → Common

### Forbidden

- Common → Plugin (would create cycle)
- Common → Main (would create cycle)
- Plugin → Main (parallel modules, no dependency)
- Main → Plugin (parallel modules, no dependency)

## Version Synchronization

All modules share the same version number, managed in parent POM:

```xml
<groupId>ai.wanaku</groupId>
<artifactId>camel-integration-capability-parent</artifactId>
<version>0.1.0-SNAPSHOT</version>
```

Child modules inherit version from parent:

```xml
<parent>
    <groupId>ai.wanaku</groupId>
    <artifactId>camel-integration-capability-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</parent>
<artifactId>camel-integration-capability-common</artifactId>
<!-- version inherited -->
```

## Transitive Dependencies

### Common Module Brings

- Camel Core libraries
- Wanaku SDK libraries
- Jackson for YAML/JSON
- JGit for Git operations
- gRPC runtime

### Plugin Module Brings

- Common module (transitive)
- Camel API (for ContextServicePlugin)

### Main Module Brings

- Common module (transitive)
- Picocli for CLI
- Log4j2 for logging

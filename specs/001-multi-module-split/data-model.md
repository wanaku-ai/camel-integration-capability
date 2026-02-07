# Data Model: Multi-Module Project Split

**Feature Branch**: `001-multi-module-split`
**Date**: 2026-02-07

## Module Entities

### Parent POM (Root Aggregator)

**Location**: `/pom.xml`
**Packaging**: `pom`

**Attributes**:
- `groupId`: `ai.wanaku`
- `artifactId`: `camel-integration-capability-parent`
- `version`: `0.1.0-SNAPSHOT`
- `modules`: `[camel-integration-capability-common, runtimes]`

**Responsibilities**:
- Define shared properties (versions, encoding)
- Manage dependency versions via `<dependencyManagement>`
- Configure shared plugins via `<pluginManagement>`
- Aggregate all child modules

---

### Common Module

**Location**: `/camel-integration-capability-common/pom.xml`
**Packaging**: `jar`

**Attributes**:
- `artifactId`: `camel-integration-capability-common`
- `parent`: `camel-integration-capability-parent`

**Dependencies** (from parent dependencyManagement):
- `ai.wanaku.sdk:capabilities-common`
- `ai.wanaku.sdk:capabilities-discovery`
- `ai.wanaku.sdk:capabilities-data-files`
- `ai.wanaku.sdk:capabilities-exchange`
- `ai.wanaku.sdk:capabilities-runtime`
- `ai.wanaku.sdk:capabilities-services-client`
- `org.apache.camel:camel-core`
- `org.apache.camel:camel-main`
- `org.apache.camel:camel-kamelet-main`
- `org.apache.camel:camel-direct`
- `org.apache.camel:camel-file`
- `org.apache.camel:camel-yaml-dsl`
- `com.fasterxml.jackson.*` (YAML, databind)
- `org.eclipse.jgit:org.eclipse.jgit`

**Resources**:
```
src/main/resources/
└── cic-version.txt         # Version info, accessible to all consumers
```

**Packages**:
```
ai.wanaku.capability.camel/
├── WanakuCamelManager.java
├── downloader/
│   ├── DownloaderFactory.java
│   ├── ResourceDownloaderCallback.java
│   ├── ResourceListBuilder.java
│   ├── ResourceRefs.java
│   └── ResourceType.java
├── grpc/
│   ├── CamelResource.java
│   ├── CamelTool.java
│   └── ProvisionBase.java
├── init/
│   ├── Initializer.java
│   ├── InitializerFactory.java
│   ├── GitInitializer.java
│   └── NoOpInitializer.java
├── model/
│   └── (all model classes)
├── spec/
│   └── rules/
│       ├── RulesProcessor.java
│       ├── RulesTransformer.java
│       ├── resources/
│       │   ├── WanakuResourceRuleProcessor.java
│       │   └── WanakuResourceTransformer.java
│       └── tools/
│           ├── WanakuToolRuleProcessor.java
│           ├── WanakuToolTransformer.java
│           └── mapping/
│               └── (all mapper classes)
└── util/
    ├── FileUtil.java
    ├── GavUtil.java
    ├── McpRulesManager.java
    ├── McpRulesReader.java
    ├── VersionHelper.java
    └── WanakuRoutesLoader.java
```

---

### Runtimes Aggregator

**Location**: `/camel-integration-capability-runtimes/pom.xml`
**Packaging**: `pom`

**Attributes**:
- `artifactId`: `camel-integration-capability-runtimes`
- `parent`: `camel-integration-capability-parent`
- `modules`: `[camel-integration-capability-plugin, camel-integration-capability-main]`

---

### Plugin Module

**Location**: `/camel-integration-capability-runtimes/camel-integration-capability-plugin/pom.xml`
**Packaging**: `jar`

**Attributes**:
- `artifactId`: `camel-integration-capability-plugin`
- `parent`: `camel-integration-capability-runtimes`

**Dependencies**:
- `ai.wanaku:camel-integration-capability-common`
- `org.apache.camel:camel-api` (for ContextServicePlugin interface)
- `org.slf4j:slf4j-api`

**Packages**:
```
ai.wanaku.capability.camel.plugin/
├── CamelIntegrationPlugin.java      # ContextServicePlugin implementation
└── PluginConfiguration.java         # Properties/env var loader

META-INF/
└── services/
    └── org.apache.camel.spi.ContextServicePlugin
```

**New Class: PluginConfiguration**

```java
public class PluginConfiguration {
    // Properties with environment variable override
    private String registrationUrl;
    private int grpcPort = 9190;
    private String registrationAnnounceAddress = "auto";
    private String serviceName = "camel";
    private String routesRef;
    private String rulesRef;
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String dependenciesRef;
    private String repositoriesList;
    private String dataDir = "/tmp";
    private String initFrom;

    // Load from properties file, then override with env vars
    public static PluginConfiguration load();

    // Getters for all properties
}
```

**New Class: CamelIntegrationPlugin**

```java
public class CamelIntegrationPlugin implements ContextServicePlugin {
    private Server grpcServer;
    private RegistrationManager registrationManager;

    @Override
    public void load(CamelContext camelContext) {
        // 1. Load configuration from properties/env
        // 2. Initialize resources (same as CamelToolMain.call())
        // 3. Start gRPC server
        // 4. Register with discovery service
    }

    @Override
    public void unload(CamelContext camelContext) {
        // 1. Deregister from discovery
        // 2. Stop gRPC server
        // 3. Cleanup resources
    }
}
```

---

### Main Module

**Location**: `/camel-integration-capability-runtimes/camel-integration-capability-main/pom.xml`
**Packaging**: `jar`

**Attributes**:
- `artifactId`: `camel-integration-capability-main`
- `parent`: `camel-integration-capability-runtimes`

**Dependencies**:
- `ai.wanaku:camel-integration-capability-common`
- `info.picocli:picocli`
- `org.apache.logging.log4j:log4j-slf4j2-impl`
- `org.apache.logging.log4j:log4j-core`

**Packages**:
```
ai.wanaku.capability.camel/
└── CamelToolMain.java    # CLI entry point (existing, moved)

src/main/resources/
└── log4j2.properties           # Logging configuration
```

**Build Plugins**:
- `maven-assembly-plugin` (jar-with-dependencies)
- `camel-maven-plugin` (prepare-fatjar)

---

## Configuration Entity

**File**: `camel-integration-capability.properties` (plugin module resource)

**Schema**:
```properties
# Registration
registration.url=
registration.announce.address=auto

# gRPC
grpc.port=9190

# Service Identity
service.name=camel

# Routes
routes.ref=
rules.ref=

# Authentication
token.endpoint=
client.id=
client.secret=

# Dependencies
dependencies=
repositories=

# Initialization
init.from=
data.dir=/tmp
```

---

## State Transitions

### Plugin Lifecycle

```
[Unloaded] --load(ctx)--> [Initializing] --success--> [Running] --unload(ctx)--> [Stopped]
                              |
                              +--failure--> [Failed]
```

### Module Build Order

```
[common] --> [plugin]
         --> [main]
```

Build order enforced by Maven reactor based on dependencies.

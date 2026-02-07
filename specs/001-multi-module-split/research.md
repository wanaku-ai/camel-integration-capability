# Research: Multi-Module Project Split

**Feature Branch**: `001-multi-module-split`
**Date**: 2026-02-07

## R1: ContextServicePlugin Implementation Pattern

**Decision**: Implement plugin as `ContextServicePlugin` with SPI discovery

**Rationale**:
- ContextServicePlugin is Camel's standard extension point for third-party initialization
- Discovered via ServiceLoader from `META-INF/services/org.apache.camel.spi.ContextServicePlugin`
- Provides `load(CamelContext)` for setup and `unload(CamelContext)` for cleanup
- Does not require modification to host application code

**Interface Requirements**:
```java
public class CamelIntegrationPlugin implements ContextServicePlugin {
    void load(CamelContext camelContext);   // Setup routes, register beans
    void unload(CamelContext camelContext); // Cleanup resources
}
```

**Alternatives Considered**:
- Standard library (rejected: no lifecycle hooks for Camel context)
- OSGi bundle (rejected: adds complexity, not widely used in target environments)

## R2: Plugin Configuration Strategy

**Decision**: Properties file + environment variables (env vars take precedence)

**Rationale**:
- Plugin cannot receive CLI arguments (no main method)
- Environment variables essential for OpenShift/Kubernetes deployments
- Properties file provides default configuration and local development flexibility
- Standard pattern: `camel-integration-capability.properties` in classpath, overrideable by env vars

**Configuration Mapping**:

| CLI Option | Property Key | Environment Variable |
|------------|--------------|---------------------|
| `--registration-url` | `registration.url` | `REGISTRATION_URL` |
| `--grpc-port` | `grpc.port` | `GRPC_PORT` |
| `--registration-announce-address` | `registration.announce.address` | `REGISTRATION_ANNOUNCE_ADDRESS` |
| `--name` | `service.name` | `SERVICE_NAME` |
| `--routes-ref` | `routes.ref` | `ROUTES_PATH` |
| `--rules-ref` | `rules.ref` | `ROUTES_RULES` |
| `--token-endpoint` | `token.endpoint` | `TOKEN_ENDPOINT` |
| `--client-id` | `client.id` | `CLIENT_ID` |
| `--client-secret` | `client.secret` | `CLIENT_SECRET` |
| `--dependencies` | `dependencies` | `DEPENDENCIES` |
| `--init-from` | `init.from` | `INIT_FROM` |
| `--repositories` | `repositories` | `REPOSITORIES` |
| `--data-dir` | `data.dir` | `DATA_DIR` |

**Alternatives Considered**:
- Properties only (rejected: not suitable for container orchestration)
- Environment only (rejected: poor local development experience)

## R3: Module Dependency Structure

**Decision**: Three-module hierarchy with parent aggregator

**Structure**:
```
/pom.xml (parent aggregator, packaging: pom)
├── camel-integration-capability-common (jar)
├── runtimes/pom.xml (sub-aggregator, packaging: pom)
│   ├── camel-integration-capability-plugin (jar)
│   └── camel-integration-capability-main (jar)
```

**Dependency Graph**:
```
common ← plugin
common ← main
```

**Rationale**:
- Common module has no internal dependencies (leaf node)
- Plugin depends only on common (for shared utilities, models, route handling)
- Main depends only on common (not on plugin - they are parallel deliverables)
- Runtimes sub-aggregator groups deployment artifacts

**Alternatives Considered**:
- Flat three modules (rejected: runtimes grouping provides clearer separation)
- Plugin depends on main (rejected: creates unnecessary coupling)

## R4: Code Distribution Across Modules

**Decision**: Categorize by dependency on CLI/runtime vs reusable logic

**Common Module Contents** (`camel-integration-capability-common`):
- `model/` - All model classes (McpSpec, etc.)
- `util/` - Utility classes (FileUtil, GavUtil, McpRulesManager, McpRulesReader, VersionHelper, WanakuRoutesLoader)
- `spec/` - Rules processing (RulesProcessor, RulesTransformer, tool/resource transformers)
- `downloader/` - Resource downloading logic
- `grpc/` - gRPC service implementations (CamelTool, CamelResource, ProvisionBase)
- `init/` - Initializers (Initializer interface, InitializerFactory, GitInitializer, NoOpInitializer)
- `WanakuCamelManager.java` - Core Camel management

**Main Module Contents** (`runtimes/camel-integration-capability-main`):
- `CamelToolMain.java` - CLI entry point with picocli
- Main-specific resources (log4j2.properties, application config)
- Dockerfile, container scripts

**Plugin Module Contents** (`runtimes/camel-integration-capability-plugin`):
- `CamelIntegrationPlugin.java` - ContextServicePlugin implementation
- `PluginConfiguration.java` - Properties/env var configuration loader
- `META-INF/services/org.apache.camel.spi.ContextServicePlugin`
- Plugin-specific resources

## R5: CI/CD Adjustments

**Decision**: Update workflows to build multi-module project

**Changes Required**:
1. `main-build.yml`: Change `mvn -B clean package` to build from root (will build all modules)
2. `Dockerfile`: Update path to jar from `target/` to `runtimes/camel-integration-capability-main/target/`
3. `pr-builds.yml`: No changes needed (builds from root)
4. `release-artifacts.yml`: Update artifact paths for release
5. `early-access.yml`: Update artifact paths

**New Deliverables**:
- `camel-integration-capability-common-X.Y.Z.jar` (library)
- `camel-integration-capability-plugin-X.Y.Z.jar` (plugin jar)
- `camel-integration-capability-main-X.Y.Z-jar-with-dependencies.jar` (standalone)
- Container image (from main module)

## R6: Documentation Updates

**Decision**: Add plugin documentation and update usage guide

**New Documentation**:
- `docs/plugin-usage.md` - Plugin installation, configuration reference, OpenShift deployment
- Update `docs/usage.md` - Reference plugin as alternative deployment option
- Update `README.md` - Mention multi-module structure and deliverables

**Configuration Reference Format**:
```markdown
## Configuration Properties

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| registration.url | REGISTRATION_URL | (required) | Wanaku registration endpoint |
...
```

# Migration Guide

This guide helps you upgrade between major versions of the Camel Integration Capability.

## Upgrading from 0.0.9 to 0.1.0

Version 0.1.0 introduces significant architectural changes, particularly around project structure and recommended deployment patterns.

### 1. Multi-Module Restructuring

The project was split into multiple Maven modules. This affects Maven coordinates and artifact selection.

**Before (0.0.9):**

```xml
<dependency>
    <groupId>ai.wanaku</groupId>
    <artifactId>camel-integration-capability</artifactId>
    <version>0.0.9</version>
</dependency>
```

**After (0.1.0):**

Choose the appropriate module based on your use case:

**Standalone CLI application:**

```xml
<dependency>
    <groupId>ai.wanaku</groupId>
    <artifactId>camel-integration-capability-main</artifactId>
    <version>0.1.0</version>
</dependency>
```

**SPI plugin for existing Camel apps:**

```xml
<dependency>
    <groupId>ai.wanaku</groupId>
    <artifactId>camel-integration-capability-plugin</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Shared library/common utilities:**

```xml
<dependency>
    <groupId>ai.wanaku</groupId>
    <artifactId>camel-integration-capability-common</artifactId>
    <version>0.1.0</version>
</dependency>
```

The JAR file names also changed:

- Main: `camel-integration-capability-main-0.1.0-jar-with-dependencies.jar`
- Plugin: `camel-integration-capability-plugin-0.1.0-shaded.jar`

### 2. Wanaku SDK Upgrade

The Wanaku Capabilities SDK was upgraded from 0.0.x to 0.1.0. This introduces breaking changes in registration APIs.

**Impact:**

- Internal registration logic updated to use new SDK APIs
- No action required for CLI users
- Plugin users: ensure your Camel app uses compatible SDK versions

### 3. Service Catalogs (Recommended Approach)

Version 0.1.0 introduces **service catalogs** as the preferred way to package and distribute routes, rules, and dependencies.

**Before (0.0.9):** Individual file references

```bash
java -jar camel-integration-capability.jar \
  --routes-ref file:///path/to/routes.yaml \
  --rules-ref file:///path/to/rules.yaml \
  --dependencies file:///path/to/deps.txt
```

**After (0.1.0):** Service catalog

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
```

Individual file references (`--routes-ref`, `--rules-ref`, `--dependencies`) are still supported for development and testing.

**Creating a service catalog:**

1. Create a directory structure:

   ```text
   my-catalog/
   ├── index.properties
   └── employee-system/
       ├── routes.camel.yaml
       ├── rules.wanaku-rules.yaml
       └── dependencies.txt
   ```

2. Define `index.properties`:

   ```properties
   catalog.name=my-catalog-v1
   catalog.services=employee-system
   catalog.routes.employee-system=employee-system/routes.camel.yaml
   catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml
   catalog.dependencies.employee-system=employee-system/dependencies.txt
   ```

3. Package as a ZIP:

   ```bash
   cd my-catalog && zip -r my-catalog-v1.zip *
   ```

4. Upload to a DataStore or file server and reference by name.

See the [examples/service-catalog](../examples/service-catalog) directory for a complete working example.

### 4. Optional Authentication

The `--client-secret` parameter is now **optional**. Authentication can be disabled in the Wanaku MCP Router.

**Before (0.0.9):** Always required

```bash
java -jar camel-integration-capability.jar \
  --client-id wanaku-service \
  --client-secret your-secret
```

**After (0.1.0):** Optional

```bash
# With authentication
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --client-id wanaku-service \
  --client-secret your-secret

# Without authentication (if Wanaku is configured to allow it)
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --client-id wanaku-service
```

### 5. Health Checks

Version 0.1.0 adds gRPC health check support. The gRPC server now starts **before** registration to ensure health probes succeed.

**Impact:**

- Kubernetes/OpenShift deployments can now use gRPC health probes
- No configuration changes required
- Health endpoint: gRPC Health Checking Protocol on the gRPC port

**Example Kubernetes health probe:**

```yaml
livenessProbe:
  grpc:
    port: 9190
  initialDelaySeconds: 10
  periodSeconds: 10
```

### 6. Route Loading Policies

Version 0.1.0 introduces configurable route loading error handling.

**Behavior:**

- **Fail-fast (default):** Application exits if any route fails to load
- **Log-and-continue:** Logs errors but continues with remaining routes

**Configuration:**
Set via environment variable or system property (exact mechanism depends on deployment mode).

### 7. Breaking Changes

#### Docker Image

The Docker image entry point was updated to use the new JAR name:

**Before (0.0.9):**

```dockerfile
ENTRYPOINT ["java", "-jar", "camel-integration-capability.jar"]
```

**After (0.1.0):**

```dockerfile
ENTRYPOINT ["java", "-jar", "camel-integration-capability-main.jar"]
```

If you have custom Docker images or scripts referencing the old JAR name, update them accordingly.

#### Artifact Coordinates

As described in section 1, all Maven coordinates changed. Update your `pom.xml` or build scripts.

#### Plugin Mode

The plugin JAR is now a separate artifact (`camel-integration-capability-plugin`) with a shaded classifier. Update your classpath configuration if embedding the plugin in an existing Camel application.

### 8. Deprecated Features

None. All features from 0.0.9 are still supported in 0.1.0, but service catalogs are now the recommended approach.

### 9. New Features

- **Service catalogs:** Versioned, self-contained route packages
- **Health checks:** gRPC health probes for Kubernetes/OpenShift
- **Exponential backoff:** Retry logic for resource downloads
- **Spotless formatting:** Code quality enforcement

### 10. Upgrade Checklist

- [ ] Update Maven coordinates in `pom.xml` (use `-main`, `-plugin`, or `-common` as appropriate)
- [ ] Update JAR file name in scripts, Docker images, and deployment manifests
- [ ] Consider migrating to service catalogs for production deployments
- [ ] Add gRPC health probes to Kubernetes/OpenShift deployments
- [ ] Test authentication with and without `--client-secret` (if applicable)
- [ ] Verify route loading behavior (fail-fast vs log-and-continue)
- [ ] Update any custom integrations using Wanaku SDK 0.0.x to 0.1.0

## Upgrading from 0.1.0 to 0.1.1

Version 0.1.1 promotes service catalogs as the recommended deployment model, makes authentication fully optional, and adds configurable Keycloak realm support.

### 1. Service Catalogs (Recommended)

Service catalogs are now the preferred way to package routes, rules, and dependencies. Individual file references (`--routes-ref`, `--rules-ref`, `--dependencies`) are still supported but should be reserved for local development.

**Before (individual file references):**

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --routes-ref file:///path/to/routes.camel.yaml \
  --rules-ref file:///path/to/rules.yaml \
  --dependencies file:///path/to/deps.txt
```

**After (service catalog):**

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
```

See the [Service Catalog Guide](service-catalog-guide.md) for creating and publishing catalogs.

### 2. Optional Authentication

The `--client-secret` parameter is no longer required. When the Wanaku MCP Router is configured without authentication, you can omit `--client-secret` entirely while still providing `--client-id` (used as the service identifier).

```bash
# Without authentication (if Wanaku allows unauthenticated access)
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --client-id wanaku-service \
  --service-catalog my-catalog-v1 \
  --service-catalog-system my-system
```

### 3. Configurable Keycloak Realm

The Keycloak realm name is fully configurable via the `--token-endpoint` parameter. There is no hardcoded default realm; use whatever realm your Keycloak instance defines.

```bash
--token-endpoint http://keycloak:8080/realms/my-realm/protocol/openid-connect/token
```

### 4. Bug Fixes

- Fixed health check race condition: gRPC server now starts before registration (#82)
- Fixed dependencies file parser ignoring lines after the first entry
- Improved route loading error messages

### 5. Dependency Updates

- Wanaku Capabilities SDK: 0.1.0 → 0.1.1
- SLF4J: 2.0.17 → 2.0.18
- Jackson: 2.21.2 → 2.21.3
- Spotless Maven Plugin: 3.4.0 → 3.5.1

### 6. Upgrade Checklist

- [ ] Migrate from individual file references to service catalogs for production deployments
- [ ] Remove `--client-secret` if running without authentication
- [ ] Update `--token-endpoint` to use your actual Keycloak realm name (no default realm)
- [ ] Update Wanaku SDK dependency to 0.1.1 if using the plugin mode

## Need Help?

- Review the [examples](../examples) directory for working configurations
- Check the [Usage Guide](usage.md) for detailed documentation
- Open an issue on [GitHub](https://github.com/wanaku-ai/camel-integration-capability/issues)

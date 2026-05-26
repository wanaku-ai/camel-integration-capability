# CLI Reference

Complete parameter reference for the Camel Integration Capability standalone application. The service uses [picocli](https://picocli.info/) for command-line parsing.

## Parameter Overview

Parameters are grouped by function: registration, authentication, route configuration, and server settings.

## Registration Parameters

Control how the service registers with the Wanaku MCP Router.

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--registration-url` | `REGISTRATION_URL` | Yes | - | URL of the Wanaku discovery service (e.g., `http://wanaku-router:8080`) |
| `--registration-announce-address` | `REGISTRATION_ANNOUNCE_ADDRESS` | Yes | `auto` | Address the router uses to call back to this service. In Kubernetes, use the service DNS name (e.g., `camel-capability.default.svc.cluster.local`). For local development, use `localhost`. |
| `--name` | `SERVICE_NAME` | No | `camel` | Service name for registration. Appears in Wanaku's service registry. Must be unique per capability instance. |
| `--initial-delay` | - | No | `5` | Initial delay before first registration attempt (seconds). Gives the gRPC server time to start. |
| `--period` | - | No | `5` | Period between registration attempts (seconds). Used during initial registration retry loop. |
| `--retries` | - | No | `12` | Maximum number of registration retries. After this, the service fails to start. Also applies to resource download retries. |
| `--wait-seconds` | - | No | `5` | Wait time between retries (seconds). Used for registration and resource downloads. |

### Registration Flow

1. Service starts gRPC server on `--grpc-port`
2. Waits `--initial-delay` seconds
3. Attempts registration with Wanaku at `--registration-url`
4. If registration fails, retries every `--period` seconds, up to `--retries` attempts
5. On success, begins downloading resources (routes, rules, dependencies)
6. After resources are loaded, marks itself as ready

**Example**:

```bash
--registration-url http://wanaku-router:8080 \
--registration-announce-address my-service.example.com \
--name employee-integration \
--retries 20 \
--wait-seconds 10
```

This configuration:

- Registers with the router at `http://wanaku-router:8080`
- Tells the router to call back at `my-service.example.com:9190`
- Identifies itself as `employee-integration`
- Retries registration up to 20 times, waiting 10 seconds between attempts

## Authentication Parameters

OAuth2/OIDC credentials for authenticating with Wanaku services.

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--client-id` | `CLIENT_ID` | Yes | - | OAuth2 client ID. Must match a client configured in your OAuth2 provider (e.g., Keycloak). |
| `--client-secret` | `CLIENT_SECRET` | No | - | OAuth2 client secret. Omit if Wanaku is running without authentication (local development). |
| `--token-endpoint` | `TOKEN_ENDPOINT` | No | Auto-detected | OAuth2/OIDC token endpoint base URL. If not specified, derived from `--registration-url`. For Keycloak, **include the realm path**: `http://keycloak:8543/realms/my-realm/`. The service appends `/protocol/openid-connect/token`. |

### Auto-Detection of Token Endpoint

If `--token-endpoint` is omitted, the service derives it from `--registration-url`:

```text
--registration-url http://wanaku-router:8080
→ token endpoint: http://wanaku-router:8080/protocol/openid-connect/token
```

This works if Wanaku and the OAuth2 provider share the same base URL. For separate OAuth2 providers (e.g., external Keycloak), specify `--token-endpoint` explicitly.

### Keycloak Configuration

For Keycloak, the token endpoint format is:

```text
http://<keycloak-host>:<port>/realms/<realm-name>/protocol/openid-connect/token
```

The service appends `/protocol/openid-connect/token`, so provide:

```bash
--token-endpoint http://keycloak:8543/realms/wanaku/
```

**Wrong**:

```bash
--token-endpoint http://keycloak:8543/
```

Without the realm path, token acquisition will fail with 404.

### Example: Full Authentication Configuration

```bash
--client-id wanaku-camel-service \
--client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv \
--token-endpoint http://keycloak:8543/realms/production/
```

### Example: No Authentication (Development)

```bash
--client-id camel-dev
```

Omit `--client-secret` when Wanaku is running without authentication. The service will skip OAuth2 token acquisition.

## Route Configuration Parameters

Control how the service loads routes, rules, and dependencies.

### Service Catalog Mode (Recommended)

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--service-catalog` | `SERVICE_CATALOG` | No | - | Name of the service catalog to use. Must match `catalog.name` in the catalog's `index.properties`. Mutually exclusive with `--routes-ref`, `--rules-ref`, and `--dependencies`. |
| `--service-catalog-system` | `SERVICE_CATALOG_SYSTEM` | Yes (if using catalog) | - | System name within the catalog. Must be listed in `catalog.services` in the catalog's `index.properties`. |

**Example**:

```bash
--service-catalog employee-system-v2 \
--service-catalog-system employee-system
```

The service will:

1. Download `employee-system-v2.zip` from Wanaku's DataStore after registration
2. Extract the catalog's `index.properties`
3. Locate the `employee-system` resources within the catalog
4. Load routes, rules, and dependencies from the extracted files

See [Service Catalog Guide](service-catalog-guide.md) for details on creating and publishing catalogs.

### Individual File References Mode

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--routes-ref` | `ROUTES_REF` | Yes (if not using catalog) | - | Reference to the Apache Camel routes YAML file. Supports `datastore://` and `file://` schemes. |
| `--rules-ref` | `RULES_REF` | No | - | Reference to the route exposure rules YAML file. Supports `datastore://` and `file://` schemes. If omitted, no routes are exposed as MCP tools/resources. |
| `--dependencies` | `DEPENDENCIES` | No | - | Reference to a text file containing a comma-separated or newline-separated list of Maven dependencies (GAV format). Supports `datastore://` and `file://` schemes. |
| `--repositories` | `REPOSITORIES` | No | Maven Central | Comma-separated list of additional Maven repository URLs to use for downloading dependencies. |

**Supported URI Schemes**:

- **`datastore://`**: Downloads the file from Wanaku's DataStore after registration. Example: `datastore://routes.camel.yaml`
- **`file://`**: Loads the file from the local filesystem. Must be an absolute path. Example: `file:///data/routes.camel.yaml`

**Example: DataStore References**:

```bash
--routes-ref datastore://employee-routes.camel.yaml \
--rules-ref datastore://employee-rules.yaml \
--dependencies datastore://employee-deps.txt
```

**Example: Local File References**:

```bash
--routes-ref file:///tmp/routes.camel.yaml \
--rules-ref file:///tmp/rules.yaml \
--dependencies file:///tmp/deps.txt
```

**Example: Custom Maven Repositories**:

```bash
--dependencies datastore://deps.txt \
--repositories https://my-private-repo.com/maven,https://repo.company.com/nexus
```

Dependencies will be downloaded from these repositories in addition to Maven Central.

### Mutual Exclusivity

`--service-catalog` is mutually exclusive with `--routes-ref`, `--rules-ref`, and `--dependencies`. You must choose one mode:

**Catalog mode**:

```bash
--service-catalog my-catalog \
--service-catalog-system my-system
```

**Individual files mode**:

```bash
--routes-ref datastore://routes.camel.yaml \
--rules-ref datastore://rules.yaml
```

**Invalid (mixing modes)**:

```bash
--service-catalog my-catalog \
--service-catalog-system my-system \
--routes-ref datastore://routes.camel.yaml  # ERROR
```

The service will reject this configuration at startup.

### Git Initialization

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--init-from` | `INIT_FROM` | No | - | Git repository URL to clone during initialization (before registration). Supports SSH (`git@github.com:org/repo.git`) and HTTPS (`https://github.com/org/repo.git`) formats. Cloned repository is placed in a subdirectory of `--data-dir`. |

**Example**:

```bash
--init-from git@github.com:wanaku-ai/wanaku-recipes.git \
--routes-ref file:///tmp/cloned-repo/routes/employee.camel.yaml \
--rules-ref file:///tmp/cloned-repo/rules/employee.yaml
```

The repository is cloned to `/tmp/cloned-repo/` (assuming `--data-dir=/tmp`), then files are loaded using `file://` references.

**Note**: Git initialization happens before registration. If the clone fails (e.g., SSH key issues, network problems), the service fails to start.

## Server Configuration Parameters

Control the gRPC server and data storage.

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--grpc-port` | `GRPC_PORT` | No | `9190` | Port the gRPC server listens on. Must match the port Wanaku uses to call back to the service. In Kubernetes, ensure this matches the Service's `targetPort`. |
| `--data-dir` | `DATA_DIR` | No | `/tmp` | Directory where downloaded files (routes, rules, dependencies) are saved. In Kubernetes, mount a volume here if `/tmp` is read-only. In Docker, the default is `/data`. |
| `--fail-fast` | - | No | `false` | If `true`, the service fails immediately if any route fails to load. If `false`, route loading errors are logged and the service continues with successfully loaded routes. |
| `--no-wait` | - | No | `false` | **Deprecated**. Previously controlled whether the service waited for resources to be available. Now ignored. |

**Example: Custom gRPC Port and Data Directory**:

```bash
--grpc-port 9191 \
--data-dir /var/camel-data
```

**Example: Fail-Fast for Development**:

```bash
--fail-fast=true
```

With this setting, the service shuts down on the first route loading error. Useful for catching syntax errors or missing dependencies early.

## Help and Version

| Parameter | Description |
|-----------|-------------|
| `-h`, `--help` | Display help message and exit. Shows all available parameters with descriptions. |

**Example**:

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar --help
```

## Complete Examples

### Minimal Configuration (Service Catalog)

```bash
java -jar camel-integration-capability-main-0.1.0-jar-with-dependencies.jar \
  --registration-url http://wanaku-router:8080 \
  --registration-announce-address my-service.example.com \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system \
  --client-id wanaku-service \
  --client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv
```

### Individual File References with Dependencies

```bash
java -jar camel-integration-capability-main-0.1.0-jar-with-dependencies.jar \
  --registration-url http://wanaku-router:8080 \
  --registration-announce-address my-service.example.com \
  --routes-ref datastore://employee.camel.yaml \
  --rules-ref datastore://employee-rules.yaml \
  --dependencies datastore://employee-deps.txt \
  --repositories https://my-repo.com/maven \
  --client-id wanaku-service \
  --client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv \
  --data-dir /data
```

### Local Development (No Authentication)

```bash
java -jar camel-integration-capability-main-0.1.0-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --routes-ref file:///workspace/routes.camel.yaml \
  --rules-ref file:///workspace/rules.yaml \
  --client-id camel-dev
```

### Git Initialization with File References

```bash
java -jar camel-integration-capability-main-0.1.0-jar-with-dependencies.jar \
  --registration-url http://wanaku-router:8080 \
  --registration-announce-address camel-capability.prod.svc.cluster.local \
  --init-from git@github.com:my-org/camel-routes.git \
  --routes-ref file:///tmp/cloned-repo/routes/production.camel.yaml \
  --rules-ref file:///tmp/cloned-repo/rules/production.yaml \
  --dependencies file:///tmp/cloned-repo/deps/production.txt \
  --client-id wanaku-service \
  --client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv \
  --data-dir /tmp
```

### Custom gRPC Port with Fail-Fast

```bash
java -jar camel-integration-capability-main-0.1.0-jar-with-dependencies.jar \
  --registration-url http://wanaku-router:8080 \
  --registration-announce-address my-service.example.com \
  --grpc-port 9191 \
  --fail-fast=true \
  --service-catalog test-catalog-v1 \
  --service-catalog-system test-system \
  --client-id wanaku-service \
  --client-secret test-secret
```

### Extended Retry Configuration

```bash
java -jar camel-integration-capability-main-0.1.0-jar-with-dependencies.jar \
  --registration-url http://wanaku-router:8080 \
  --registration-announce-address my-service.example.com \
  --retries 30 \
  --wait-seconds 10 \
  --initial-delay 10 \
  --period 10 \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system \
  --client-id wanaku-service \
  --client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv
```

This gives the service 5 minutes to register (30 retries × 10 seconds), useful in environments where the router or OAuth2 provider takes time to start.

## Environment Variables

All CLI parameters can be set via environment variables. The mapping follows a standard pattern:

**CLI parameter → Environment variable**:

- `--registration-url` → `REGISTRATION_URL`
- `--client-id` → `CLIENT_ID`
- `--service-catalog` → `SERVICE_CATALOG`
- etc.

**Example in Kubernetes**:

```yaml
env:
  - name: REGISTRATION_URL
    value: "http://wanaku-router:8080"
  - name: REGISTRATION_ANNOUNCE_ADDRESS
    value: "camel-capability.default.svc.cluster.local"
  - name: SERVICE_CATALOG
    value: "employee-system-v2"
  - name: SERVICE_CATALOG_SYSTEM
    value: "employee-system"
  - name: CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: wanaku-credentials
        key: client-id
  - name: CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: wanaku-credentials
        key: client-secret
```

CLI parameters take precedence over environment variables. If both are set, the CLI value is used.

## Parameter Validation

The service validates parameters at startup. Common validation errors:

**Missing required parameters**:

```text
Missing required option: '--registration-url=<registrationUrl>'
```

**Mutually exclusive parameters**:

```text
--service-catalog is mutually exclusive with --routes-ref, --rules-ref, and --dependencies
```

**Service catalog without system**:

```text
--service-catalog-system is required when --service-catalog is used
```

**Routes reference missing (individual file mode)**:

```text
Either --routes-ref or --service-catalog must be provided
```

Validation errors cause the service to exit immediately with a non-zero status code.

## Default Values Summary

| Parameter | Default |
|-----------|---------|
| `--grpc-port` | `9190` |
| `--name` | `camel` |
| `--retries` | `12` |
| `--wait-seconds` | `5` |
| `--initial-delay` | `5` |
| `--period` | `5` |
| `--data-dir` | `/tmp` (CLI), `/data` (Docker) |
| `--fail-fast` | `false` |
| `--registration-announce-address` | `auto` |

All other parameters have no default and must be provided (or are optional).

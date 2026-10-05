# CLI Reference

Complete parameter reference for the Camel Integration Capability standalone application. The service uses [picocli](https://picocli.info/) for command-line parsing.

## Parameter Overview

Parameters are grouped by function: resource loading, MCP server configuration, and general settings.

## Resource Loading Parameters

Control how the service loads routes and dependencies.

### Service Catalog Mode (Recommended)

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--service-catalog` | `SERVICE_CATALOG` | No | - | Name of the service catalog to use. Must match `catalog.name` in the catalog's `index.properties`. Mutually exclusive with `--routes-ref` and `--dependencies`. |
| `--service-catalog-system` | `SERVICE_CATALOG_SYSTEM` | Yes (if using catalog) | - | System name within the catalog. Must be listed in `catalog.services` in the catalog's `index.properties`. |

**Example**:

```bash
--service-catalog employee-system-v2 \
--service-catalog-system employee-system
```

The service will:

1. Download `employee-system-v2.zip` from Wanaku's DataStore
2. Extract the catalog's `index.properties`
3. Locate the `employee-system` resources within the catalog
4. Load routes and dependencies from the extracted files

See [Service Catalog Guide](service-catalog-guide.md) for details on creating and publishing catalogs.

### Individual File References Mode

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--routes-ref` | `ROUTES_REF` | Yes (if not using catalog) | - | Reference to the Apache Camel routes YAML file. Supports `datastore://` and `file://` schemes. |
| `--dependencies` | `DEPENDENCIES` | No | - | Reference to a text file containing a comma-separated or newline-separated list of Maven dependencies (GAV format). Supports `datastore://` and `file://` schemes. |
| `--repositories` | `REPOSITORIES` | No | Maven Central | Comma-separated list of additional Maven repository URLs to use for downloading dependencies. |

**Supported URI Schemes**:

- **`datastore://`**: Downloads the file from Wanaku's DataStore. Example: `datastore://routes.camel.yaml`
- **`file://`**: Loads the file from the local filesystem. Must be an absolute path. Example: `file:///data/routes.camel.yaml`

**Example: DataStore References**:

```bash
--routes-ref datastore://employee-routes.camel.yaml \
--dependencies datastore://employee-deps.txt
```

**Example: Local File References**:

```bash
--routes-ref file:///tmp/routes.camel.yaml \
--dependencies file:///tmp/deps.txt
```

### Mutual Exclusivity

`--service-catalog` is mutually exclusive with `--routes-ref` and `--dependencies`. You must choose one mode:

**Catalog mode**:

```bash
--service-catalog my-catalog \
--service-catalog-system my-system
```

**Individual files mode**:

```bash
--routes-ref datastore://routes.camel.yaml \
--dependencies datastore://deps.txt
```

### Git Initialization

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--init-from` | `INIT_FROM` | No | - | Git repository URL to clone during initialization (before resource download). Supports SSH and HTTPS formats. Cloned repository is placed in a subdirectory of `--data-dir`. |

**Example**:

```bash
--init-from git@github.com:wanaku-ai/wanaku-recipes.git \
--routes-ref file:///tmp/cloned-repo/routes/employee.camel.yaml
```

## MCP Server Parameters

Control the built-in MCP server (HTTP/SSE transport).

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--mcp-port` | `MCP_PORT` | No | `8080` | Port the MCP server listens on. Clients connect to this port using HTTP/SSE transport. |
| `--health-port` | - | No | `8081` | Port for the HTTP health check endpoints (`/observe/health`, `/observe/health/live`, `/observe/health/ready`), served by Camel's management HTTP server and backed by the Camel Health Check API. Use these for Kubernetes liveness/readiness probes. |
| `--no-health` | - | No | `false` | If `true`, the HTTP health check endpoints are disabled. |
| `--mcp-tags` | `MCP_TAGS` | No | - | Comma-separated tags for MCP tool filtering. Only `ai-tool:` routes whose tags match will be exposed as MCP tools. If omitted, all `ai-tool:` routes are exposed. |

**Example: Custom MCP Port**:

```bash
--mcp-port 9090
```

**Example: Tag-Based Filtering**:

```bash
--mcp-tags hr,employee
```

This exposes only `ai-tool:` routes tagged with `hr` or `employee`.

## Server Configuration Parameters

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--registration-url` | `REGISTRATION_URL` | Yes | `http://localhost:8080` | The Wanaku server URL used for downloading resources (routes, catalogs, dependencies). |
| `--name` | `SERVICE_NAME` | No | `camel` | Service name identifier. |
| `--retries` | - | No | `12` | Maximum number of download retries. |
| `--wait-seconds` | - | No | `5` | Wait time between retries in seconds. Used as the initial delay for exponential backoff. |
| `--data-dir` | `DATA_DIR` | No | `/tmp` | Directory where downloaded files (routes, dependencies) are saved. In Docker, the default is `/data`. |
| `--fail-fast` | - | No | `false` | If `true`, the service fails immediately if any route fails to load. If `false`, route loading errors are logged and the service continues with successfully loaded routes. |

## Help

| Parameter | Description |
|-----------|-------------|
| `-h`, `--help` | Display help message and exit. |

## Complete Examples

### Minimal Configuration (Service Catalog)

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://wanaku-server:8080 \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
```

### Individual File References with Dependencies

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://wanaku-server:8080 \
  --routes-ref datastore://employee.camel.yaml \
  --dependencies datastore://employee-deps.txt \
  --repositories https://my-repo.com/maven \
  --data-dir /data
```

### Local Development

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --routes-ref file:///workspace/routes.camel.yaml \
  --mcp-port 9090
```

### Git Initialization with File References

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://wanaku-server:8080 \
  --init-from git@github.com:my-org/camel-routes.git \
  --routes-ref file:///tmp/cloned-repo/routes/production.camel.yaml \
  --dependencies file:///tmp/cloned-repo/deps/production.txt \
  --data-dir /tmp
```

### Tag Filtering with Fail-Fast

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://wanaku-server:8080 \
  --fail-fast=true \
  --mcp-tags hr,employee \
  --service-catalog test-catalog-v1 \
  --service-catalog-system test-system
```

## Environment Variables

All CLI parameters can be set via environment variables. The mapping follows a standard pattern:

**Example in Kubernetes**:

```yaml
env:
  - name: REGISTRATION_URL
    value: "http://wanaku-server:8080"
  - name: SERVICE_CATALOG
    value: "employee-system-v2"
  - name: SERVICE_CATALOG_SYSTEM
    value: "employee-system"
  - name: MCP_PORT
    value: "8080"
  - name: MCP_TAGS
    value: "hr,employee"
```

CLI parameters take precedence over environment variables. If both are set, the CLI value is used.

## Parameter Validation

The service validates parameters at startup. Common validation errors:

**Mutually exclusive parameters**:

```text
--service-catalog is mutually exclusive with --routes-ref and --dependencies
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
| `--mcp-port` | `8080` |
| `--name` | `camel` |
| `--retries` | `12` |
| `--wait-seconds` | `5` |
| `--data-dir` | `/tmp` (CLI), `/data` (Docker) |
| `--fail-fast` | `false` |
| `--registration-url` | `http://localhost:8080` |

All other parameters have no default and must be provided (or are optional).

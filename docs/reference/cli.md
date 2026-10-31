---
title: CLI reference
description: All command-line options and environment variables of the Camel Integration Capability, with their default values and validation rules.
---

# CLI Reference

Complete parameter reference for the Camel Integration Capability standalone application. The service uses [picocli](https://picocli.info/) for command-line parsing.

## Parameter Overview

Parameters are grouped by function: resource loading, MCP server configuration, Wanaku self-registration, and general settings.

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

See [Serve a service catalog](../build/service-catalogs.md).

### Individual File References Mode

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--routes-ref` | `ROUTES_PATH` | Yes (if not using catalog) | - | Reference to the Apache Camel routes YAML file. Supports `datastore://` and `file://` schemes. |
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

Control the built-in MCP server (streamable HTTP transport).

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--mcp-port` | `MCP_PORT` | No | `9090` | Port the MCP server listens on. Clients connect to this port at the `/mcp` path. The same port serves the health endpoints (`/observe/health`, `/observe/health/live`, `/observe/health/ready`). |
| `--mcp-tags` | `MCP_TAGS` | No | `wanaku` | Comma-separated tags for MCP tool filtering. Only `ai-tool:` routes whose tags match are exposed as MCP tools. A route without tags is never exposed. |

**Example: Custom MCP Port**:

```bash
--mcp-port 9090
```

**Example: Tag-Based Filtering**:

```bash
--mcp-tags hr,employee
```

This exposes only `ai-tool:` routes tagged with `hr` or `employee`.

## Wanaku Self-registration Parameters

Register the capability as an MCP forward in Wanaku on startup, and remove the forward on shutdown. Self-registration is off by default. See [Self-registration with Wanaku](../operate/self-registration.md) for the lifecycle, the ownership rules, and a Kubernetes example.

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--register` | `WANAKU_REGISTER` | No | `false` | Turns on self-registration. Accepts `--register`, `--register=true` or `--register=false`. |
| `--wanaku-url` | `WANAKU_URL` | Yes (with `--register`) | - | Wanaku management API URL, for example `http://wanaku:8080`. Must be an `http` or `https` URL. |
| `--namespace` | `WANAKU_NAMESPACE` | No | `default` | Wanaku namespace of the forward. |
| `--advertised-address` | `MCP_ADDRESS` | Yes (with `--register`) | - | MCP URL that Wanaku uses to reach this capability, for example `http://my-tool.tools.svc:9090/mcp`. Must be an `http` or `https` URL. |
| `--wanaku-token-env` | `WANAKU_TOKEN_ENV` | No | - | Name of the environment variable that contains a bearer token for the Wanaku API. The variable must be set and not empty. |

With `--register`, `--name` is the forward name, and `--retries` and `--wait-seconds` also control the registration retries.

**Example**:

```bash
export WANAKU_TOKEN=my-token
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --routes-ref file:///workspace/routes.camel.yaml \
  --name echo-tool \
  --register \
  --wanaku-url http://localhost:8080 \
  --advertised-address http://localhost:9090/mcp \
  --wanaku-token-env WANAKU_TOKEN
```

## Server Configuration Parameters

| Parameter | Environment Variable | Required | Default | Description |
|-----------|---------------------|----------|---------|-------------|
| `--registration-url` | `REGISTRATION_URL` | Yes | `http://localhost:8080` | The Wanaku Barn base URL used to download `datastore://` resources and service catalogs. It does not register the capability (see `--register`). |
| `--name` | `SERVICE_NAME` | No | `camel` | Service name identifier. The forward name with `--register`. |
| `--retries` | - | No | `12` | Maximum number of download retries. Also the maximum number of registration retries with `--register`. |
| `--wait-seconds` | - | No | `5` | Wait time between retries in seconds. Used as the initial delay for exponential backoff of downloads, and as the fixed wait between registration attempts. |
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

The container image (`quay.io/wanaku/camel-integration-capability`) reads the environment variables in the tables above and gives them to the JAR as options. `--fail-fast`, `--retries` and `--wait-seconds` have no environment variable. When you run the JAR directly, use the command-line options. The JAR reads only one environment variable: the variable that `--wanaku-token-env` names.

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
    value: "9090"
  - name: MCP_TAGS
    value: "hr,employee"
```

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

**Self-registration options**:

```text
--wanaku-url is required with --register
--advertised-address is required with --register
--name must contain only letters, digits, '.', '_' and '-' with --register: <name>
--register requires the MCP server (--mcp-port greater than 0)
The environment variable named by --wanaku-token-env is not set: <variable>
```

Validation errors cause the service to exit immediately with a non-zero status code (2 for usage errors).
A failed self-registration causes the service to exit with status code 1.

## Default Values Summary

| Parameter | Default |
|-----------|---------|
| `--mcp-port` | `9090` |
| `--mcp-tags` | `wanaku` |
| `--name` | `camel` |
| `--register` | `false` |
| `--namespace` | `default` |
| `--retries` | `12` |
| `--wait-seconds` | `5` |
| `--data-dir` | `/tmp` (CLI), `/data` (Docker) |
| `--fail-fast` | `false` |
| `--registration-url` | `http://localhost:8080` |

All other parameters have no default and must be provided (or are optional).

## Next steps

- [Write and load routes](../build/routes.md): use the route options.
- [Self-registration with Wanaku](../operate/self-registration.md): use the registration options.

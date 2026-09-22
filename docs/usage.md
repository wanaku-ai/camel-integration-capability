# Camel Integration Capability

A capability service for the [Wanaku MCP Router](https://wanaku.ai) that provides [Apache Camel](https://camel.apache.org) route execution capabilities.

This service downloads Camel routes, runs them using Apache Camel's Main runtime, and exposes `ai-tool:` routes as MCP tools via a built-in HTTP/SSE MCP server.

## Overview

This service:

- Downloads and executes Apache Camel routes defined in YAML format
- Exposes routes using the `ai-tool:` URI format as MCP tools
- Provides an HTTP/SSE MCP server for tool invocation

> [TIP]
> Design your Camel routes with ease using the [Kaoto Integration Designer](http://kaoto.io) for Apache Camel.

<!-- -->

> [NOTE]
> Upgrading from a previous version? See the [Migration Guide](migration-guide.md) for breaking changes and upgrade steps.

## Related Guides

- **[CLI Reference](cli-reference.md)** - Complete command-line parameter reference
- **[Service Catalog Guide](service-catalog-guide.md)** - Creating and publishing service catalogs
- **[Troubleshooting](troubleshooting.md)** - Common issues and solutions

## Requirements

- Java 21 or higher
- Maven 3.6+ for building
- Access to a Wanaku server (for downloading resources)

## Running this Capability

### Required Parameters

One of the following is required to provide routes:

- `--service-catalog`: Name of the service catalog to load routes and dependencies from (recommended). Requires `--service-catalog-system`
- `--routes-ref`: Reference to the Apache Camel routes YAML file. Supports `datastore://` and `file://` schemes

### Optional Parameters

- `--registration-url`: URL of the Wanaku server for downloading resources (default: `http://localhost:8080`)
- `--service-catalog-system`: The system name within the service catalog (required when using `--service-catalog`)
- `--dependencies`: Comma-separated list of dependencies. Supports `datastore://` and `file://` schemes
- `--init-from`: Git repository URL to clone during initialization (SSH or HTTPS format)
- `--mcp-port`: Port for the MCP server (default: 8080)
- `--mcp-tags`: Comma-separated tags for filtering which `ai-tool:` routes to expose
- `--health-port`: HTTP port for health check endpoints `/observe/health`, `/observe/health/live`, `/observe/health/ready` (default: 8081)
- `--no-health`: Disable the HTTP health check endpoints
- `--name`: Service name (default: "camel")
- `--retries`: Maximum download retries (default: 12)
- `--wait-seconds`: Wait time between retries in seconds (default: 5)
- `--data-dir`: Directory where downloaded files will be saved (default: `/tmp` for CLI, `/data` for Docker)

> [NOTE]
> `--service-catalog` is mutually exclusive with `--routes-ref` and `--dependencies`.
> When using a service catalog, all resources are extracted from the catalog automatically.

### URI Schemes

The service supports multiple URI schemes for resource references:

- **datastore://**: Fetches files from the Wanaku DataStore service.
- **file://**: References local files (absolute paths required)

### Basic Example Using a Service Catalog (Recommended)

Service catalogs bundle routes and dependencies into a single versioned artifact stored in Wanaku.

```bash
java -jar target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --name employee-system \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
```

### Basic Example Using Individual References

For cases where routes and dependencies are managed separately (e.g., during development):

```bash
java -jar target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --name camel-core \
  --routes-ref file:///path/to/routes.camel.yaml \
  --dependencies file:///path/to/deps.txt \
  --mcp-port 9090
```

## Deploying the Service

The service can be deployed to Kubernetes or OpenShift using Wanaku's operator.

### Using a Service Catalog (Recommended)

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: Wanaku
metadata:
  name: wanaku-dev
spec:
  router:
    image: quay.io/wanaku/wanaku-router-backend:latest
    imagePullPolicy: Always
  capabilities:
    - name: employee-system
      type: camel-integration-capability
      image: quay.io/wanaku/camel-integration-capability:latest
      env:
        - name: SERVICE_CATALOG
          value: "employee-system-v2"
        - name: SERVICE_CATALOG_SYSTEM
          value: "employee-system"
```

### Using Individual References

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: Wanaku
metadata:
  name: wanaku-dev
spec:
  router:
    image: quay.io/wanaku/wanaku-router-backend:latest
    imagePullPolicy: Always
  capabilities:
    - name: employee-system
      type: camel-integration-capability
      image: quay.io/wanaku/camel-integration-capability:latest
      env:
        - name: ROUTES_REF
          value: "datastore://employee-backend.camel.yaml"
        - name: DEPENDENCIES
          value: "datastore://employee-backend-dependencies.txt"
```

#### Configuration Reference

| Parameter                | Description                                                | Example                        |
|--------------------------|------------------------------------------------------------|--------------------------------|
| `SERVICE_CATALOG`        | Name of the service catalog (recommended)                  | `employee-system-v2`           |
| `SERVICE_CATALOG_SYSTEM` | System name within the catalog (required with above)       | `employee-system`              |
| `ROUTES_REF`             | Reference to the Camel routes YAML file                    | `datastore://route.camel.yaml` |
| `DEPENDENCIES`           | Reference to a text file containing a list of dependencies | `datastore://dependencies.txt` |
| `INIT_FROM`              | Git repository URL to clone during startup                 | `git@github.com:org/repo.git`  |
| `DATA_DIR`               | Directory where downloaded files are saved                 | `/data`                        |
| `REPOSITORIES`           | Additional Maven repositories to use                       | `http://repo.com`              |
| `MCP_PORT`               | Port for the MCP server                                    | `8080`                         |
| `MCP_TAGS`               | Comma-separated tags for tool filtering                    | `hr,employee`                  |

> [NOTE]
> `SERVICE_CATALOG` is mutually exclusive with `ROUTES_REF` and `DEPENDENCIES`.

### Troubleshooting

Common issues and solutions:

**Routes not found:**

```bash
# Verify git clone succeeded
kubectl exec deployment/camel-integration-capability -- ls -la /data
```

**MCP server not responding:**

```bash
# Check environment variables
kubectl exec deployment/camel-integration-capability -- env | grep -E "(REGISTRATION|MCP)"

# View application logs
kubectl logs -f deployment/camel-integration-capability -c camel-integration-capability
```

**Connection refused errors:**

```bash
# Verify service is running
kubectl get svc camel-integration-capability

# Check pod status
kubectl describe pod -l app=camel-integration-capability

# Test connectivity
kubectl run test-pod --rm -it --image=busybox -- telnet camel-integration-capability 8080
```

## Designing Routes

The easiest way to design the routes for this project, is to use a visual editor such as [Kaoto](http://kaoto.io) or
[Camel Karavan](http://camel.apache.org/karavan) to design the routes.

### ai-tool: Route Format

Routes that should be exposed as MCP tools use the `ai-tool:` URI format. This format embeds tool metadata directly in the route definition:

```yaml
- route:
    id: get-employee-info
    from:
      uri: ai-tool:get-employee-info
      parameters:
        description: "Fetches core profile data for a specific employee"
      steps:
        - toD: https://api.example.com/employees/${header.employeeId}
```

The `ai-tool:` prefix tells Camel's MCP server to expose this route as an MCP tool. The tool name, description, and parameters are all defined within the route YAML.

### Standard Routes

Routes that should NOT be exposed as MCP tools use standard Camel URIs (e.g., `direct:`, `timer:`). These routes can still be invoked internally by `ai-tool:` routes.

### Handling Dependencies

The capability only comes with a subset of the Apache Camel dependencies.
External dependencies can be provided in a text file:

```text
org.apache.camel:camel-http:4.22.0,org.apache.camel:camel-jackson:4.22.0
```

Then reference the file:

- `--dependencies datastore://filename.txt` if using the data store
- `--dependencies file:///path/to/filename.txt` if using the git initializer

> [NOTE]
> Repositories for dependencies can be set using the `--repositories` option, which receives a comma-separated list of
> repository URLs.

## Running the Capability and Exposing Camel Routes

Route files can be provided to the capability using one of the following methods:

1. **From a Wanaku Service Catalog** (recommended): Bundles routes and dependencies into a single versioned artifact
2. **From Wanaku's Data Store**: Uses Wanaku's Data Store to download individual files
3. **Built-in Git initialization**: Use `--init-from` to clone a repository during startup
4. **Init container**: Use a separate container to clone files before the main container starts
5. **Volume mounts**: Mount ConfigMaps or persistent volumes containing route files

### Using a Service Catalog

> [TIP]
> For a complete guide on creating and publishing service catalogs, see the [Service Catalog Guide](service-catalog-guide.md).

This is the recommended way to obtain route files. A service catalog is a versioned ZIP archive stored in Wanaku that bundles routes and optionally dependencies for one or more systems.

```bash
java -jar target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --name employee-system \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
```

### Using Wanaku's Data Store

```bash
java -jar target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --name camel-core \
  --routes-ref datastore://promote-employee.camel.yaml \
  --dependencies datastore://promote-employee-dependencies.txt \
  --data-dir /tmp/camel-data
```

### Using Git Initialization

```bash
java -jar target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --name camel-core \
  --init-from git@github.com:wanaku-ai/wanaku-recipes.git \
  --routes-ref file:///tmp/cloned-repo/routes/promote-employee.camel.yaml \
  --dependencies file:///tmp/cloned-repo/dependencies/promote-employee-dependencies.txt \
  --data-dir /tmp
```

# Camel Integration Capability

A capability service for the [Wanaku](https://wanaku.ai) that provides [Apache Camel](https://camel.apache.org) route execution capabilities.

This service downloads Camel routes, runs them using Apache Camel's Main runtime, and exposes `ai-tool:` routes as MCP tools via a built-in HTTP/SSE MCP server.

## Overview

This service:

- Downloads and executes Apache Camel routes defined in YAML format
- Exposes routes using the `ai-tool:` URI format as MCP tools
- Provides an HTTP/SSE MCP server for tool invocation

> [!TIP]
> Design your Camel routes with ease using the [Kaoto Integration Designer](http://kaoto.io) for Apache Camel.

<!-- -->

> [!NOTE]
> Upgrading from a previous version? See the [Migration Guide](migration-guide.md) for breaking changes and upgrade steps.

## Related Guides

- **[OpenShift Echo Tool](openshift-echo-tool.md)** - Test a file-based echo route and explore deployment details
- **[CLI Reference](cli-reference.md)** - Complete command-line parameter reference
- **[Self-registration with Wanaku](#self-registration-with-wanaku)** - Register the capability as a Wanaku forward on startup
- **[Operations](operations.md)** - Health probes, sizing, and production practices
- **[Service Catalog Guide](service-catalog-guide.md)** - Creating and publishing service catalogs
- **[Troubleshooting](troubleshooting.md)** - Common issues and solutions

## Requirements

- Java 21 or higher
- Maven 3.6+ for building
- Access to Wanaku Barn when you download resources (`datastore://` references or service catalogs)
- Access to the Wanaku management API when you use self-registration (`--register`)

## Running this Capability

### Required Parameters

One of the following is required to provide routes:

- `--service-catalog`: Name of the service catalog to load routes and dependencies from (recommended). Requires `--service-catalog-system`
- `--routes-ref`: Reference to the Apache Camel routes YAML file. Supports `datastore://` and `file://` schemes

### Optional Parameters

- `--registration-url`: Base URL of Wanaku Barn, used to download `datastore://` resources and service catalogs (default: `http://localhost:8080`). This option does not register the capability. For registration, see [Self-registration with Wanaku](#self-registration-with-wanaku)
- `--service-catalog-system`: The system name within the service catalog (required when using `--service-catalog`)
- `--dependencies`: Comma-separated list of dependencies. Supports `datastore://` and `file://` schemes
- `--init-from`: Git repository URL to clone during initialization (SSH or HTTPS format)
- `--mcp-port`: Port for the MCP server and the health endpoints (default: 9090)
- `--mcp-tags`: Comma-separated tags for filtering which `ai-tool:` routes to expose
- `--name`: Service name (default: "camel"). Used as the forward name with `--register`
- `--retries`: Maximum download and registration retries (default: 12)
- `--wait-seconds`: Wait time between retries in seconds (default: 5)
- `--data-dir`: Directory where downloaded files will be saved (default: `/tmp` for CLI, `/data` for Docker)

> [!NOTE]
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

For production deployments on Kubernetes or OpenShift, use Wanaku's operator.
The [OpenShift Echo Tool guide](openshift-echo-tool.md) provides a standalone
manifest for testing and exploring deployment details.

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
| `MCP_PORT`               | Port for the MCP server                                    | `9090`                         |
| `MCP_TAGS`               | Comma-separated tags for tool filtering                    | `hr,employee`                  |
| `WANAKU_REGISTER`        | Register the capability as a Wanaku forward                | `true`                         |
| `WANAKU_URL`             | Wanaku management API URL                                  | `http://wanaku:8080`           |
| `WANAKU_NAMESPACE`       | Wanaku namespace for the forward                           | `default`                      |
| `MCP_ADDRESS`            | MCP address that Wanaku uses to reach the capability       | `http://my-tool:9090/mcp`      |
| `WANAKU_TOKEN_ENV`       | Name of the variable that contains the Wanaku bearer token | `WANAKU_TOKEN`                 |

> [!NOTE]
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
kubectl run test-pod --rm -it --image=busybox -- telnet camel-integration-capability 9090
```

## Self-registration with Wanaku

By default, the capability does not contact Wanaku. You add it to Wanaku yourself, for example with `wanaku forwards add`.
With self-registration, the capability adds itself as an MCP forward when it starts and removes the forward when it stops.
Use it when the capability runs on a platform that starts and replaces instances for you, such as Kubernetes.

### Options

| Option | Environment Variable | Default | Description |
|--------|----------------------|---------|-------------|
| `--register` | `WANAKU_REGISTER` | `false` | Turns on self-registration. |
| `--wanaku-url` | `WANAKU_URL` | - | Wanaku management API URL, for example `http://wanaku:8080`. Required with `--register`. |
| `--name` | `SERVICE_NAME` | `camel` | Forward name. Use only letters, digits, `.`, `_` and `-`. Forward names are global in Wanaku: use a different name for each capability. |
| `--namespace` | `WANAKU_NAMESPACE` | `default` | Wanaku namespace of the forward. |
| `--advertised-address` | `MCP_ADDRESS` | - | MCP URL that Wanaku uses to reach this capability, for example `http://my-tool.tools.svc:9090/mcp`. Required with `--register`. |
| `--wanaku-token-env` | `WANAKU_TOKEN_ENV` | - | Name of the environment variable that contains a bearer token for the Wanaku API. The token itself is never a command-line argument. |
| `--retries` | - | `12` | Maximum number of retries when Wanaku cannot be reached or returns a 5xx status. |
| `--wait-seconds` | - | `5` | Wait time between registration attempts, in seconds. |

`--registration-url` is a different option. It sets the Wanaku Barn base URL for downloads. It does not register the capability.

### Lifecycle

On startup, the capability does these steps in sequence:

1. It loads the routes and starts Camel and the MCP server.
2. It connects to its own MCP server at `http://127.0.0.1:<mcp-port>/mcp`. It sends the MCP `initialize` request and the `tools/list` request. It continues only if the server exposes one or more tools.
3. It sends `POST <wanaku-url>/api/v1/forwards` with the forward name, the advertised address, the namespace, and these labels:
   - `cic.instance`: a random identifier for this process
   - `cic.version`: the capability version
   - `cic.routes-digest`: the SHA-256 digest of the loaded routes file
4. Wanaku connects to the advertised address to discover the tools. The registration succeeds only if Wanaku reports the forward as available and discovers one or more tools.
5. The readiness probe changes to UP.

If Wanaku cannot be reached or returns a 5xx status, the capability tries again up to `--retries` times.
If registration fails, the capability removes the forward if it owns it, stops Camel, and exits with status 1.
Kubernetes then restarts the pod.

### Shutdown and Ownership

When the capability receives a stop signal, it does these steps before it stops Camel:

1. It sets the readiness probe to DOWN.
2. It reads the forward with `GET <wanaku-url>/api/v1/forwards/<name>`.
3. If the `cic.instance` label is the identifier of this process, it deletes the forward.
4. If another instance owns the forward, it keeps the forward. This prevents an old instance from removing the registration of its replacement.
5. If the forward does not exist (HTTP 404), it does nothing.

Each HTTP call has a 10-second timeout. Set `terminationGracePeriodSeconds` to 30 or more.

> [!NOTE]
> Wanaku has no conditional delete. If a replacement registers between the ownership check and the delete, the old instance can remove the new forward. Restart the replacement, or register it again, to recover.

### Health Probes

The MCP server port (default 9090) also serves the Camel health endpoints:

| Path | Use |
|------|-----|
| `/observe/health/live` | Liveness probe |
| `/observe/health/ready` | Readiness probe |
| `/observe/health` | All health checks |

The readiness endpoint includes the `wanaku-registration` check. This check is DOWN until registration succeeds, and DOWN while the capability stops. When self-registration is off, this check is UP after startup.

> [!IMPORTANT]
> Wanaku must reach the advertised address before the pod is ready. A Kubernetes Service sends traffic only to ready pods by default. Set `publishNotReadyAddresses: true` on the Service that the advertised address uses. Otherwise, discovery fails and the pod never becomes ready.

### Kubernetes Example

```yaml
apiVersion: v1
kind: Service
metadata:
  name: echo-tool
spec:
  selector:
    app: echo-tool
  publishNotReadyAddresses: true
  ports:
    - name: mcp
      port: 9090
      targetPort: mcp
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: echo-tool
spec:
  replicas: 1
  selector:
    matchLabels:
      app: echo-tool
  template:
    metadata:
      labels:
        app: echo-tool
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: echo-tool
          image: quay.io/wanaku/camel-integration-capability:latest
          env:
            - name: ROUTES_PATH
              value: "file:///routes/echo-tool.camel.yaml"
            - name: SERVICE_NAME
              value: "echo-tool"
            - name: WANAKU_REGISTER
              value: "true"
            - name: WANAKU_URL
              value: "http://wanaku:8080"
            - name: WANAKU_NAMESPACE
              value: "default"
            - name: MCP_ADDRESS
              value: "http://echo-tool.tools.svc:9090/mcp"
            - name: WANAKU_TOKEN_ENV
              value: "WANAKU_TOKEN"
            - name: WANAKU_TOKEN
              valueFrom:
                secretKeyRef:
                  name: wanaku-api-token
                  key: token
          ports:
            - name: mcp
              containerPort: 9090
          livenessProbe:
            httpGet:
              path: /observe/health/live
              port: mcp
            initialDelaySeconds: 15
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /observe/health/ready
              port: mcp
            initialDelaySeconds: 10
            periodSeconds: 5
          volumeMounts:
            - name: routes
              mountPath: /routes
              readOnly: true
      volumes:
        - name: routes
          configMap:
            name: echo-tool-routes
```

Omit `WANAKU_TOKEN_ENV` and `WANAKU_TOKEN` when the Wanaku API does not require authentication.

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

> [!NOTE]
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

> [!TIP]
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

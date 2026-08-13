# Migration Guide

This guide helps you upgrade between major versions of the Camel Integration Capability.

## Upgrading from 0.1.x to 0.2.0

Version 0.2.0 is a major architectural change. The custom gRPC bridge, authentication layer, and rules YAML files have been removed. The capability now uses Apache Camel 4.22's built-in MCP server with the `ai-tool:` route format.

### 1. Architecture Changes

**Before (0.1.x):** CIC registered with Wanaku via gRPC, used OAuth2 for authentication, and required separate rules YAML files to expose routes as MCP tools.

**After (0.2.0):** CIC downloads routes and runs them using Camel Main. Routes using the `ai-tool:` URI format are automatically exposed as MCP tools via a built-in HTTP/SSE MCP server.

### 2. Rules YAML to ai-tool: Migration

The most significant change is the migration from separate rules YAML files to the `ai-tool:` route format. Tool metadata is now embedded directly in the route definition.

**Before (rules YAML + separate route):**

```yaml
# rules.yaml
mcp:
  tools:
    - get-employee-info:
        route:
          id: "get-employee-route"
        description: "Retrieve employee information by ID"
        properties:
          - name: employeeId
            type: string
            description: The employee ID
            required: true
            mapping:
              type: header
              name: EMPLOYEE_ID
```

```yaml
# routes.camel.yaml
- route:
    id: get-employee-route
    from:
      uri: direct:get-employee-route
      steps:
        - toD: https://api.example.com/employees/${header.EMPLOYEE_ID}
```

**After (single ai-tool: route):**

```yaml
# routes.camel.yaml
- route:
    id: get-employee-info
    from:
      uri: ai-tool:get-employee-info
      parameters:
        description: "Retrieve employee information by ID"
      steps:
        - toD: https://api.example.com/employees/${header.employeeId}
```

**Key differences:**

- The `ai-tool:` URI replaces `direct:` for routes exposed as MCP tools
- Tool description is defined as a route parameter, not in a separate file
- Parameter mapping is handled automatically by the MCP server
- No separate rules file is needed

### 3. Removed CLI Parameters

The following CLI parameters have been removed:

| Removed Parameter | Reason |
|-------------------|--------|
| `--registration-announce-address` | No more service registration |
| `--grpc-port` | No more gRPC server |
| `--rules-ref` | Rules embedded in routes via `ai-tool:` format |
| `--token-endpoint` | No more OAuth2 authentication |
| `--client-id` | No more OAuth2 authentication |
| `--client-secret` | No more OAuth2 authentication |
| `--initial-delay` | No more registration retry loop |
| `--period` | No more registration retry loop |

### 4. New CLI Parameters

| New Parameter | Default | Description |
|---------------|---------|-------------|
| `--mcp-port` | `8080` | Port for the built-in MCP server (HTTP/SSE transport) |
| `--mcp-tags` | (none) | Comma-separated tags for filtering which `ai-tool:` routes to expose |

### 5. Removed Modules

The following modules have been removed:

- **camel-integration-capability-plugin**: The SPI plugin for embedding into existing Camel applications
- **camel-integration-capability-common**: Shared gRPC services and models

The project now has a simpler structure focused on the standalone CLI application.

### 6. Docker Image Changes

**Environment variables removed:**

- `REGISTRATION_ANNOUNCE_ADDRESS`
- `GRPC_PORT`
- `ROUTES_RULES`
- `TOKEN_ENDPOINT`
- `CLIENT_ID`
- `CLIENT_SECRET`

**Environment variables added:**

- `MCP_TAGS` -- Comma-separated tags for tool filtering
- `MCP_PORT` -- MCP server port (default: 8080)

**Port change:**

- Before: `EXPOSE 9190` (gRPC)
- After: `EXPOSE 8080` (MCP HTTP/SSE)

### 7. Service Catalog Changes

Service catalogs no longer require a `catalog.rules.<system>` entry. The rules are now embedded in the route files themselves.

**Before:**

```properties
catalog.name=employee-system-v2
catalog.services=employee-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
```

**After:**

```properties
catalog.name=employee-system-v2
catalog.services=employee-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
```

### 8. Kubernetes Deployment Changes

**Before:**

```yaml
capabilities:
  - name: employee-system
    type: camel-integration-capability
    image: quay.io/wanaku/camel-integration-capability:latest
    env:
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

**After:**

```yaml
capabilities:
  - name: employee-system
    type: camel-integration-capability
    image: quay.io/wanaku/camel-integration-capability:latest
    env:
      - name: SERVICE_CATALOG
        value: "employee-system-v2"
      - name: SERVICE_CATALOG_SYSTEM
        value: "employee-system"
      - name: MCP_PORT
        value: "8080"
```

### 9. Health Check Changes

**Before:** gRPC health probes on port 9190

**After:** HTTP probes on the MCP server port (default 8080)

```yaml
livenessProbe:
  httpGet:
    path: /
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 10
```

### 10. Upgrade Checklist

- [ ] Convert rules YAML files to `ai-tool:` route format
- [ ] Remove `--rules-ref` from CLI arguments and deployment manifests
- [ ] Remove authentication parameters (`--client-id`, `--client-secret`, `--token-endpoint`)
- [ ] Remove `--grpc-port` and `--registration-announce-address`
- [ ] Add `--mcp-port` if a non-default port is needed
- [ ] Add `--mcp-tags` if tag-based filtering is needed
- [ ] Update Docker port mapping from 9190 to 8080
- [ ] Update Kubernetes health probes from gRPC to HTTP
- [ ] Update service catalog `index.properties` to remove `catalog.rules.*` entries
- [ ] Remove the plugin module dependency if used
- [ ] Test all routes with the new format

## Upgrading from 0.0.9 to 0.1.0

See the [0.1.0 migration section in git history](https://github.com/wanaku-ai/camel-integration-capability) for details on the multi-module restructuring and service catalog introduction.

## Need Help?

- Review the [examples](../examples) directory for working configurations
- Check the [Usage Guide](usage.md) for detailed documentation
- Open an issue on [GitHub](https://github.com/wanaku-ai/camel-integration-capability/issues)

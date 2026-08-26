# Migration Guide

This guide helps you upgrade between major versions of the Camel Integration Capability.

## Upgrading from 0.1.x to 0.2.0

Version 0.2.0 is a major architectural change. The custom gRPC bridge, authentication layer, and rules YAML files have been removed. The capability now uses Apache Camel 4.22's built-in MCP server (`camel-mcp-server`) together with its new unified tool abstraction (`camel-ai-tool`), exposed through the `ai-tool:` route format.

### 1. Architecture Changes

**Before (0.1.x):** CIC registered with Wanaku via gRPC, used OAuth2 for authentication, and required separate rules YAML files to expose routes as MCP tools.

**After (0.2.0):** CIC downloads routes and runs them using Camel Main. Routes started with the `ai-tool:` URI are registered in Camel's `AiToolRegistry`, and the built-in `McpServerBridge` exposes them as MCP tools over HTTP/SSE -- provided the route's `tags` match `--mcp-tags` (see below). The tool's name, description, and parameters all live inside the route; there's no separate rules file and no registration handshake with Wanaku.

### 2. Rules YAML to ai-tool: Migration

The most significant change is the migration from separate rules YAML files to the `ai-tool:` route format introduced by Camel's new `camel-ai-tool` component. Tool metadata -- description, parameters, and tags -- now lives directly in the route, and Camel's built-in MCP server picks it up automatically. There's no separate rules file to keep in sync.

Converting an existing route is a three-step process:

1. **Replace the consumer.** Drop the `direct:` endpoint the old rules file pointed at and start the route with `ai-tool:<tool-name>` instead.
2. **Move the rules-file properties onto the route.** Each `properties` entry from the rules file becomes a `parameter.<name>: <type>` / `parameter.<name>.description` pair under `from.parameters`, and the tool's `description` moves there too. Also add a `tags` parameter matching `--mcp-tags` -- unlike the old rules file, a route with no matching tag is never exposed over MCP (see the note below).
3. **Delete the rules file and launch directly.** Drop `--rules-ref` -- just point `--routes-ref` at the route file. There's no registration step.

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
        tags: employee
        parameter.employeeId: string
        parameter.employeeId.description: "The employee ID"
    steps:
      - toD: https://api.example.com/employees/${header.employeeId}
```

**Key differences:**

- The `ai-tool:` URI replaces `direct:` for routes exposed as MCP tools
- Each rules-file property becomes a `parameter.<name>` / `parameter.<name>.description` pair on the route itself, instead of a `mapping` block in a separate file
- The route now reads the parameter directly off its own name (`header.employeeId`), not a renamed header (`header.EMPLOYEE_ID`) -- the old `mapping.name` override is gone, so rename inside the route body if you still need the old header name
- No separate rules file is needed -- the route is self-describing
- `tags` is not just a filter, it's the exposure switch: a route with no `tags` at all is registered but permanently excluded from MCP -- untagged tools are never exposed, by design, as a security boundary against untrusted MCP clients. Give every route you want reachable over MCP a tag that matches `--mcp-tags` (`wanaku` by default)

#### Body-passthrough tools

Tools that forward the whole request body instead of mapping individual fields use the reserved `parameter.wanaku_body` parameter. This isn't new: the old rules schema already recognized a bare `wanaku_body` property (no `mapping:` block needed) and mapped it to the message body by naming convention -- only its declaration site moves, from the rules file to the route itself. The example below is adapted from wanaku-barn's tool routes:

**Before:**

```yaml
- route:
    id: kafka-request-reply
    description: Send a Kafka request and wait for the correlated reply
    from:
      uri: direct:wanaku
    steps:
      - setExchangePattern:
          pattern: InOut
      - to:
          uri: kafka:{{kafka.request.topic}}?brokers={{kafka.brokers}}
  mcp:
    tools:
      - name: send-message-to-kafka
        routeId: kafka-request-reply
        description: Send a message to Kafka and wait for the correlated reply
        properties:
          - name: wanaku_body
            type: string
            description: The message to send to Kafka
            required: true
```

**After:**

```yaml
- route:
    id: kafka-request-reply
    from:
      uri: ai-tool:send-message-to-kafka
      parameters:
        tags: kafka
        description: "Send a message to Kafka and wait for the correlated reply"
        parameter.wanaku_body: string
        parameter.wanaku_body.description: "The message to send to Kafka"
    steps:
      - setExchangePattern:
          pattern: InOut
      - to:
          uri: kafka:{{kafka.request.topic}}?brokers={{kafka.brokers}}
```

> [TIP]
> The [wanaku-barn migration commit](https://github.com/wanaku-ai/wanaku-barn/commit/63396ca48b23119fa72793e7dfb93ca2fc4ea2c5) converts a full set of production tool routes -- HTTP, Kafka, JMS, Jira, mail, OpenAI, vector search, and more -- from `direct:wanaku` + rules YAML to `ai-tool:` in one pass. It's a good reference for parameter shapes not covered above.

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

The following module has been removed:

- **camel-integration-capability-plugin**: The SPI plugin for embedding into existing Camel applications

`camel-integration-capability-common` was not removed, but its scope shrank considerably: the gRPC service definitions and rules models are gone, and it now only holds small shared utilities (e.g. version reporting).

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
- [ ] Add a matching `tags` parameter to every `ai-tool:` route -- without one, the route is never exposed over MCP
- [ ] Update Docker port mapping from 9190 to 8080
- [ ] Update Kubernetes health probes from gRPC to HTTP
- [ ] Update service catalog `index.properties` to remove `catalog.rules.*` entries
- [ ] Remove the plugin module dependency if used
- [ ] Test all routes with the new format

### 11. Known Limitations

A couple of things to keep in mind while migrating to 0.2.0:

- **wanaku-barn backend**: still catching up with the router in several areas. If you're migrating a production deployment, prefer running against the Wanaku router directly rather than through barn for now.
- **Operator support**: the Wanaku operator has been updated for the new deployment shape, but this path is not yet covered by automated tests. Validate operator-based deployments carefully before relying on them in production.

## Upgrading from 0.0.9 to 0.1.0

See the [0.1.0 migration section in git history](https://github.com/wanaku-ai/camel-integration-capability) for details on the multi-module restructuring and service catalog introduction.

## Need Help?

- Review the [examples](../examples) directory for working configurations
- Check the [Usage Guide](usage.md) for detailed documentation
- Read the [Camel 4.22 AI Tools & MCP Server announcement](https://camel.apache.org/blog/2026/08/camel-ai-tools-mcp-422/index.html) for background on `camel-ai-tool` and `camel-mcp-server`
- Open an issue on [GitHub](https://github.com/wanaku-ai/camel-integration-capability/issues)

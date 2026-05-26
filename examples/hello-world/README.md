# Hello World Example

This is a minimal example demonstrating the basic functionality of the Camel Integration Capability.

## What It Does

The example exposes a single route that:

1. Accepts a greeting message as input
2. Logs it to the console
3. Returns a formatted greeting response

## Files

- **hello-quote.camel.yaml**: A simple Camel route that processes greeting messages
- **hello-quote-rules.yaml**: MCP tool configuration exposing the route to AI agents

## Running the Example

### Prerequisites

- Java 21 or higher
- The Camel Integration Capability JAR built or downloaded
- A running Wanaku MCP Router instance
- OAuth2/OIDC credentials (or authentication disabled in Wanaku)

### Start the Service

```bash
java -jar ../../camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --name hello-world \
  --routes-ref file://$(pwd)/hello-quote.camel.yaml \
  --rules-ref file://$(pwd)/hello-quote-rules.yaml \
  --client-id wanaku-service \
  --client-secret your-secret
```

### Test via AI Agent

Once registered with Wanaku, AI agents can invoke the `sends-greeting` tool:

```text
AI: Send a greeting with message "World"
```

Expected response:

```text
Hello World from route-3104
```

## Key Concepts

### Automatic Parameter Mapping

This example uses **automatic parameter mapping**. The MCP parameter `wanaku_body` is automatically mapped to the Camel message body because it follows the naming convention `wanaku_body`.

The Camel route accesses it via `${body}`:

```yaml
- log:
    message: Hello ${body}
```

### Route Exposure

The rules file defines which routes are exposed as MCP tools:

```yaml
tools:
  - sends-greeting:
      route:
        id: "route-3104"
```

This makes the route available to AI agents via the Wanaku MCP Router.

## Next Steps

- Try the [Employee System Example](../employee-system) for a more realistic scenario with explicit parameter mapping
- Learn about [Service Catalogs](../service-catalog) for production deployments
- Review the [Usage Guide](../../docs/usage.md) for detailed configuration options

# Hello World Example

This is a minimal example demonstrating the basic functionality of the Camel Integration Capability.

## What It Does

The example exposes a single route that:

1. Accepts a greeting message as input
2. Logs it to the console
3. Returns a formatted greeting response

## Files

- **hello-quote.camel.yaml**: A simple Camel route that processes greeting messages

## Running the Example

### Prerequisites

- Java 21 or higher
- The Camel Integration Capability JAR built or downloaded
- A running Wanaku server instance (for datastore mode) or use local files

### Start the Service

```bash
java -jar ../../camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --name hello-world \
  --routes-ref file://$(pwd)/hello-quote.camel.yaml
```

### Test via AI Agent

Once the MCP server is running, AI agents can invoke the tool through the Wanaku MCP Router.

## Key Concepts

### ai-tool: Route Format

Routes that use the `ai-tool:` URI format are automatically exposed as MCP tools by the built-in MCP server. The tool name, description, and parameters are defined within the route YAML.

## Next Steps

- Try the [Employee System Example](../employee-system) for a more realistic scenario with multiple routes
- Learn about [Service Catalogs](../service-catalog) for production deployments
- Review the [Usage Guide](../../docs/usage.md) for detailed configuration options

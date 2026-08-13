# Employee System Example

This example demonstrates a realistic employee management system integration using multiple routes.

## What It Does

The example exposes multiple MCP tools that interact with a backend employee service:

1. **get-employee-information**: Fetches core profile data (name, ID, level, days in level)
2. **get-employee-reviews**: Displays performance reviews, ratings, and manager feedback
3. **get-employee-compensation**: Fetches current and historical pay details

The routes demonstrate:

- The `ai-tool:` route format for MCP tool exposure
- HTTP backend integration
- Conditional logic (restricting executive data)
- Multi-step route orchestration (complete profile aggregation)

## Files

- **employee-backend.camel.yaml**: Camel routes for employee data operations
- **employee-backend-dependencies.txt**: Required Camel components (camel-http)

## Running the Example

### Prerequisites

- Java 21 or higher
- The Camel Integration Capability JAR built or downloaded
- A running Wanaku server instance (for datastore mode) or use local files
- A backend employee service running at `http://employee-backend-service:8081`

> **Note:** Replace `employee-backend-service:8081` in the routes file with your actual backend URL before running.

### Start the Service

```bash
java -jar ../../camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --name employee-system \
  --routes-ref file://$(pwd)/employee-backend.camel.yaml \
  --dependencies file://$(pwd)/employee-backend-dependencies.txt
```

### Test via AI Agent

Once the MCP server is running, AI agents can invoke the tools:

```text
AI: Get employee information for employee ID 12345
```

Expected response: JSON containing employee profile data from the backend.

## Key Concepts

### ai-tool: Routes

Routes use the `ai-tool:` URI format to be automatically exposed as MCP tools. The tool description and parameters are defined directly in the route YAML.

### Dynamic Dependencies

The `employee-backend-dependencies.txt` file specifies additional Camel components to download at runtime:

```text
org.apache.camel:camel-http:4.22.0
```

### Multi-Step Routes

The `get-employee-complete-profile` route demonstrates route orchestration by composing results from multiple sub-routes into a single JSON response.

### Conditional Logic

The `get-employee-information` route includes conditional logic to restrict access to executive data.

## Customization

### Change Backend URL

Edit `employee-backend.camel.yaml` and replace all occurrences of `employee-backend-service:8081` with your actual backend URL.

### Add More Endpoints

To add a new employee endpoint:

1. Add a new `ai-tool:` route in `employee-backend.camel.yaml`
2. Define the tool description and parameters in the route

## Next Steps

- Try the [Service Catalog Example](../service-catalog) to package this as a reusable catalog
- Review the [Usage Guide](../../docs/usage.md) for detailed configuration options
- Learn about [route orchestration patterns](https://camel.apache.org/manual/enterprise-integration-patterns.html) in Apache Camel

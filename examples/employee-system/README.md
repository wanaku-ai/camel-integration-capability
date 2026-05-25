# Employee System Example

This example demonstrates a realistic employee management system integration using explicit parameter mapping and multiple routes.

## What It Does

The example exposes three MCP tools that interact with a backend employee service:

1. **get-employee-information**: Fetches core profile data (name, ID, level, days in level)
2. **get-employee-reviews**: Displays performance reviews, ratings, and manager feedback
3. **get-employee-compensation**: Fetches current and historical pay details

The routes demonstrate:
- Explicit parameter mapping (MCP parameter names → Camel headers)
- HTTP backend integration
- Conditional logic (restricting executive data)
- Multi-step route orchestration (complete profile aggregation)

## Files

- **employee-backend.camel.yaml**: Four Camel routes for employee data operations
- **employee-backend-rules.yaml**: MCP tool configuration with explicit parameter mapping
- **employee-backend-dependencies.txt**: Required Camel components (camel-http)

## Running the Example

### Prerequisites

- Java 21 or higher
- The Camel Integration Capability JAR built or downloaded
- A running Wanaku MCP Router instance
- OAuth2/OIDC credentials (or authentication disabled in Wanaku)
- A backend employee service running at `http://employee-backend-service:8081`

> **Note:** Replace `employee-backend-service:8081` in the routes file with your actual backend URL before running.

### Start the Service

```bash
java -jar ../../camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --name employee-system \
  --routes-ref file://$(pwd)/employee-backend.camel.yaml \
  --rules-ref file://$(pwd)/employee-backend-rules.yaml \
  --dependencies file://$(pwd)/employee-backend-dependencies.txt \
  --client-id wanaku-service \
  --client-secret your-secret
```

### Test via AI Agent

Once registered with Wanaku, AI agents can invoke the tools:

```
AI: Get employee information for employee ID 12345
```

Expected response: JSON containing employee profile data from the backend.

## Key Concepts

### Explicit Parameter Mapping

Unlike the [hello-world example](../hello-world), this example uses **explicit parameter mapping** to control how MCP parameters map to Camel headers.

In the rules file:
```yaml
properties:
  - name: employeeId
    type: int
    description: The employee ID to retrieve information for
    required: true
    mapping:
      type: header
      name: EMPLOYEE_ID
```

This maps the MCP parameter `employeeId` → Camel header `EMPLOYEE_ID`.

The route accesses it via:
```yaml
- toD:
    uri: http://employee-backend-service:8081/employee/${header.EMPLOYEE_ID}/information
```

### Dynamic Dependencies

The `employee-backend-dependencies.txt` file specifies additional Camel components to download at runtime:

```
org.apache.camel:camel-http:4.18.2
```

This allows routes to use the HTTP component without pre-packaging all possible dependencies.

### Multi-Step Routes

The `get-employee-complete-profile` route demonstrates route orchestration:
1. Calls `direct:employee-information`
2. Stores the result in an exchange property
3. Calls `direct:employee-reviews`
4. Stores the result
5. Calls `direct:employee-compensation`
6. Combines all results into a single JSON response

This shows how to compose complex workflows from simpler routes.

### Conditional Logic

The `get-employee-information` route includes conditional logic:
```yaml
- choice:
    when:
      - simple:
          expression: ${body} contains 'Z10'
        steps:
          - setBody:
              constant: '{"error":"Employee information for executives is restricted"}'
```

This demonstrates how to enforce business rules within routes.

## Customization

### Change Backend URL

Edit `employee-backend.camel.yaml` and replace all occurrences of `employee-backend-service:8081` with your actual backend URL.

### Add More Endpoints

To add a new employee endpoint:

1. Add a new route in `employee-backend.camel.yaml`
2. Add a new tool in `employee-backend-rules.yaml`
3. Define parameter mappings as needed

## Next Steps

- Try the [Service Catalog Example](../service-catalog) to package this as a reusable catalog
- Review the [Usage Guide](../../docs/usage.md#parameter-to-header-mapping) for detailed parameter mapping documentation
- Learn about [route orchestration patterns](https://camel.apache.org/manual/enterprise-integration-patterns.html) in Apache Camel

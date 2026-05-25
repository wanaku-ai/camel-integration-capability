# Rules Schema Reference

Complete reference for the route exposure rules YAML format. These rules define which Apache Camel routes are exposed to AI agents as MCP tools or resources.

## Overview

The rules file is a YAML document that maps Camel routes to MCP (Model Context Protocol) tools and resources. Tools are invocable operations (e.g., "fetch employee data"). Resources are passive data sources (e.g., "employee performance history").

**File location**: Specified via `--rules-ref` (individual file mode) or included in a service catalog.

**Purpose**: Without a rules file, your Camel routes exist but aren't accessible to AI agents. The rules file is the bridge between Camel's internal routing and Wanaku's external API.

## Top-Level Structure

```yaml
mcp:
  tools:
    - <tool-name>:
        route:
          id: "<camel-route-id>"
        description: "<tool description>"
        namespace: "<optional-namespace>"
        properties:
          - name: <param-name>
            type: <param-type>
            description: <param-description>
            required: <true|false>
            mapping:
              type: header
              name: <camel-header-name>
  resources:
    - <resource-name>:
        route:
          id: "<camel-route-id>"
        description: "<resource description>"
        namespace: "<optional-namespace>"
```

**Root element**: `mcp`

**Sections**:

- `tools`: List of MCP tools (invocable operations)
- `resources`: List of MCP resources (passive data sources)

Both are optional. You can define only tools, only resources, or both.

## Tool Definitions

Tools are operations AI agents can invoke. Each invocation executes the mapped Camel route.

### Minimal Tool Definition

```yaml
mcp:
  tools:
    - get-employee-info:
        route:
          id: "get-employee-route"
        description: "Retrieve employee information by ID"
```

**Fields**:

- **Tool name** (`get-employee-info`): The identifier AI agents use to invoke the tool. Must be unique within this rules file.
- **`route.id`** (required): ID of the Camel route to execute. Must match a route's `id` in your routes YAML.
- **`description`** (required): Human-readable description shown to AI agents. Should explain what the tool does and when to use it.

This minimal definition uses **automatic parameter mapping**: all MCP parameters are mapped to Camel headers with a `Wanaku.` prefix.

### Full Tool Definition (Explicit Mapping)

```yaml
mcp:
  tools:
    - initiate-employee-promotion:
        route:
          id: "route-3103"
        description: "Initiate the promotion process for an employee"
        namespace: hr-operations
        properties:
          - name: employee
            type: string
            description: The employee ID to promote
            required: true
            mapping:
              type: header
              name: EMPLOYEE
          - name: newLevel
            type: string
            description: The new organizational level
            required: false
            mapping:
              type: header
              name: NEW_LEVEL
```

**Additional fields**:

- **`namespace`** (optional): Namespace for organizing tools in Wanaku's router. Related tools can share a namespace (e.g., `hr-operations`). If omitted, the tool is registered in the default namespace.
- **`properties`** (optional): List of parameter definitions. When defined, **only these parameters are mapped** (filtered mapping). When omitted, **all parameters are mapped automatically** (automatic mapping).

**Never mix automatic and explicit mapping for the same tool.** Either define `properties` with mappings or omit `properties` entirely.

## Tool Field Reference

### Tool Name

**Format**: YAML key, typically kebab-case.

**Constraints**:

- Must be unique within the rules file
- Case-sensitive
- Allowed characters: letters, numbers, hyphens, underscores
- Should be descriptive and action-oriented

**Good examples**:

- `get-employee-info`
- `update-inventory-quantity`
- `send-notification-email`

**Avoid**:

- `tool1`, `tool2` (not descriptive)
- `Employee Info` (spaces not recommended)
- `getEmployeeInfo` (camelCase works but kebab-case is more conventional)

### route.id (required)

**Type**: String

**Description**: ID of the Camel route to execute when the tool is invoked.

**Constraints**:

- Must match a route's `id` in your routes YAML exactly (case-sensitive)
- The route must exist and be successfully loaded
- One route can be mapped to multiple tools (each with different parameter mappings)

**Example**:

Routes YAML:
```yaml
- route:
    id: get-employee-route
    from:
      uri: direct:get-employee-route
      steps:
        - log:
            message: "Fetching employee ${header.Wanaku.employeeId}"
```

Rules YAML:
```yaml
mcp:
  tools:
    - get-employee-info:
        route:
          id: "get-employee-route"  # Must match
```

**What happens if the ID doesn't match?**

The tool is registered with Wanaku but invocations will fail with:

```
Route 'wrong-route-id' not found
```

### description (required)

**Type**: String

**Description**: Human-readable explanation of what the tool does. Shown to AI agents when they're deciding which tool to use.

**Best practices**:

- Use imperative or descriptive phrasing: "Retrieve employee information" or "Fetches employee data"
- Mention what the tool returns: "Returns employee name, title, and department"
- Note any constraints: "Only returns active employees"
- Keep it concise (1-2 sentences)

**Good examples**:

```yaml
description: "Retrieve employee information by ID, including name, title, and department"
```

```yaml
description: "Initiate the promotion process for an employee. Returns a promotion ticket ID."
```

**Avoid**:

```yaml
description: "This tool does stuff with employees"  # Too vague
```

```yaml
description: "Calls the backend HR API via HTTP GET using the /employees/{id} endpoint and returns JSON"  # Too technical
```

AI agents use the description to decide when to invoke the tool. Focus on **what**, not **how**.

### namespace (optional)

**Type**: String

**Description**: Namespace for organizing related tools within Wanaku's router.

**Default**: If omitted, the tool is registered in the default namespace.

**Use cases**:

- Grouping tools by domain: `hr-operations`, `payroll`, `inventory`
- Separating production and test tools: `prod-tools`, `test-tools`
- Multi-tenant environments: `tenant-a`, `tenant-b`

**Example**:

```yaml
mcp:
  tools:
    - get-employee-info:
        route:
          id: "get-employee-route"
        description: "Retrieve employee information"
        namespace: hr-data

    - initiate-promotion:
        route:
          id: "promotion-route"
        description: "Initiate employee promotion"
        namespace: hr-operations
```

Both tools are registered but in different namespaces. AI agents can filter or prioritize tools by namespace.

**Constraints**:

- Allowed characters: letters, numbers, hyphens, underscores
- Case-sensitive
- No spaces

### properties (optional)

**Type**: List of property definitions

**Description**: Defines the parameters the tool accepts. Each property maps to a Camel header.

**When to define properties**:

- You want explicit control over parameter names (no `Wanaku.` prefix)
- You need validation (required vs optional parameters)
- You want to document parameters for AI agents
- You need to restrict which parameters are passed to the route

**When to omit properties**:

- During development/prototyping
- You want all parameters passed through automatically
- You're okay with the `Wanaku.` prefix on all headers

**Important**: Defining `properties` switches to **filtered mapping mode**. Only the defined parameters are mapped. Any additional parameters sent by AI agents are ignored.

## Property Field Reference

Each property in the `properties` list defines one parameter.

### name (required)

**Type**: String

**Description**: The parameter name AI agents use when invoking the tool.

**Example**:

```yaml
properties:
  - name: employeeId
```

AI agents invoke the tool with:

```json
{
  "employeeId": "12345"
}
```

**Constraints**:

- Case-sensitive
- Should use camelCase or snake_case
- No spaces

### type (required)

**Type**: String (parameter type)

**Description**: The parameter's data type. Used for validation and documentation.

**Supported types**:

- `string` — Text values
- `int` or `integer` — Whole numbers
- `number` — Floating-point numbers
- `boolean` — `true` or `false`
- `object` — Complex JSON objects
- `array` — Lists

**Example**:

```yaml
properties:
  - name: employeeId
    type: string
  - name: includeHistory
    type: boolean
  - name: limit
    type: int
```

**Note**: The capability converts all parameter values to strings when creating Camel headers. Complex types like `object` and `array` are JSON-serialized.

### description (required)

**Type**: String

**Description**: Human-readable description of the parameter. Shown to AI agents.

**Best practices**:

- Explain what the parameter controls
- Mention valid values or formats if relevant
- Note constraints (e.g., "Must be a valid employee ID")

**Example**:

```yaml
properties:
  - name: employeeId
    type: string
    description: The employee ID to retrieve information for (e.g., EMP-12345)
  - name: includeHistory
    type: boolean
    description: Whether to include employment history in the response
```

### required (required)

**Type**: Boolean (`true` or `false`)

**Description**: Whether the parameter is mandatory.

**Example**:

```yaml
properties:
  - name: employeeId
    type: string
    description: The employee ID
    required: true  # AI agents must provide this

  - name: includeHistory
    type: boolean
    description: Include employment history
    required: false  # Optional
```

**Validation**:

- If `required: true` and the parameter is missing, the invocation fails with an error
- If `required: false`, the parameter is optional and may be omitted

### mapping (optional)

**Type**: Object with `type` and `name` fields

**Description**: Defines how the parameter is mapped to a Camel header.

**Fields**:

- **`mapping.type`** (required): Mapping type. Currently only `header` is supported.
- **`mapping.name`** (required): The Camel header name to create.

**Example**:

```yaml
properties:
  - name: employee
    type: string
    description: The employee ID
    required: true
    mapping:
      type: header
      name: EMPLOYEE
```

**Result**:

When invoked with `{"employee": "EMP-789"}`, the Camel route receives:

- Header `EMPLOYEE` with value `"EMP-789"`

**If mapping is omitted**:

The parameter name is used directly as the header name (without the `Wanaku.` prefix, since you're in explicit mapping mode).

**Example without mapping**:

```yaml
properties:
  - name: employeeId
    type: string
    description: The employee ID
    required: true
```

When invoked with `{"employeeId": "12345"}`, the route receives:

- Header `employeeId` with value `"12345"`

**Automatic mapping (no properties defined) vs Explicit mapping (properties with mapping)**:

| Mode | Properties Defined? | Mapping Defined? | Header Name |
|------|---------------------|------------------|-------------|
| Automatic | No | N/A | `Wanaku.<paramName>` |
| Explicit (default name) | Yes | No | `<paramName>` |
| Explicit (custom name) | Yes | Yes | `<mapping.name>` |

## Parameter Mapping Strategies

The capability supports two strategies for mapping MCP parameters to Camel headers. You choose a strategy per tool, not globally.

### Strategy 1: Automatic Mapping (No Properties)

When you omit the `properties` field, **all** MCP parameters are automatically mapped to Camel headers with a `Wanaku.` prefix.

**Rules YAML**:

```yaml
mcp:
  tools:
    - get-employee-info:
        route:
          id: "get-employee-route"
        description: "Retrieve employee information"
```

**AI agent invocation**:

```json
{
  "employeeId": "12345",
  "includeHistory": "true"
}
```

**Camel headers created**:

- `Wanaku.employeeId` → `"12345"`
- `Wanaku.includeHistory` → `"true"`

**Accessing in routes**:

```yaml
- route:
    id: get-employee-route
    from:
      uri: direct:get-employee-route
      steps:
        - log:
            message: "Fetching employee ${header.Wanaku.employeeId}"
        - toD: "https://api.example.com/employees/${header.Wanaku.employeeId}"
```

**Pros**:

- Simple configuration
- All parameters are available
- Flexible for prototyping

**Cons**:

- `Wanaku.` prefix required in routes
- No parameter validation
- No documentation for AI agents

### Strategy 2: Explicit Mapping (With Properties)

When you define `properties`, **only those properties are mapped** to Camel headers. Additional parameters sent by AI agents are ignored.

**Rules YAML**:

```yaml
mcp:
  tools:
    - initiate-promotion:
        route:
          id: "promotion-route"
        description: "Initiate employee promotion"
        properties:
          - name: employee
            type: string
            description: The employee ID
            required: true
            mapping:
              type: header
              name: EMPLOYEE
          - name: newLevel
            type: string
            description: The new organizational level
            required: true
            mapping:
              type: header
              name: NEW_LEVEL
```

**AI agent invocation**:

```json
{
  "employee": "EMP-789",
  "newLevel": "Senior Manager",
  "extraParam": "ignored"
}
```

**Camel headers created**:

- `EMPLOYEE` → `"EMP-789"`
- `NEW_LEVEL` → `"Senior Manager"`
- `extraParam` is **not mapped** (not defined in properties)

**Accessing in routes**:

```yaml
- route:
    id: promotion-route
    from:
      uri: direct:promotion-route
      steps:
        - log:
            message: "Promoting ${header.EMPLOYEE} to ${header.NEW_LEVEL}"
        - toD: "https://hr-api.example.com/promotions?emp=${header.EMPLOYEE}"
```

**Pros**:

- Full control over header names
- Parameter validation (required fields)
- Clear API documentation for AI agents
- Restricts which parameters reach the backend

**Cons**:

- More verbose configuration
- Must update rules file when adding parameters

### Choosing a Strategy

| Use Case | Strategy |
|----------|----------|
| Prototyping, flexible parameters | Automatic |
| Production, strict API contracts | Explicit |
| Need custom header names | Explicit |
| Want parameter validation | Explicit |
| Simple pass-through scenarios | Automatic |

**Best practice**: Start with automatic mapping during development. Switch to explicit mapping for production deployments.

## Resource Definitions

Resources are passive data sources. Unlike tools, they're not invoked — instead, AI agents subscribe to them or query them periodically.

**Key difference from tools**:

- **Tools**: Execute the route's logic (the route body)
- **Resources**: Access only the route's endpoint URI via a consumer template (the route body is **not executed**)

**Resource routes must have auto-start disabled**. Otherwise, the route starts consuming data at startup, which isn't what you want for resources.

### Minimal Resource Definition

```yaml
mcp:
  resources:
    - employee-performance-history:
        route:
          id: "performance-history-route"
        description: "Employee performance history records"
```

**Routes YAML** (note `autoStartup: false`):

```yaml
- route:
    id: performance-history-route
    autoStartup: false
    from:
      uri: sql:SELECT * FROM performance_reviews WHERE employee_id = :#employeeId
```

**How it works**:

When an AI agent accesses the `employee-performance-history` resource, the capability:

1. Looks up the route by ID (`performance-history-route`)
2. Extracts the endpoint URI (`sql:SELECT ...`)
3. Uses a Camel consumer template to fetch data from that endpoint
4. Returns the data to the AI agent

The route's `steps` (if any) are **not executed**. Only the endpoint is accessed.

### Full Resource Definition

```yaml
mcp:
  resources:
    - employee-performance-history:
        route:
          id: "performance-history-route"
        description: "Historical performance review records for employees"
        namespace: hr-data
```

**Fields**:

- **Resource name** (`employee-performance-history`): Identifier AI agents use. Must be unique.
- **`route.id`** (required): ID of the Camel route. The route must have `autoStartup: false`.
- **`description`** (required): Human-readable description.
- **`namespace`** (optional): Namespace for organizing resources.

### Resource Field Reference

Same as tools, except:

- **No `properties` field**: Resources don't have parameters in the same way tools do. Data is accessed via the endpoint URI, which may have its own parameter syntax (e.g., SQL query parameters).

### Resource Constraints

**1. Auto-start must be disabled**

```yaml
- route:
    id: my-resource-route
    autoStartup: false  # Required for resources
    from:
      uri: file:/data/reports?noop=true
```

If you forget this, the route starts consuming data at startup, which can cause:

- Unwanted side effects (e.g., files being moved or deleted)
- Resource contention
- Confusing behavior (the route is running but it's supposed to be a passive resource)

**2. Data must be convertible to String**

The capability returns resource data as a string. If the endpoint returns binary data or complex objects, ensure they can be serialized to a string (e.g., JSON, XML, CSV).

**Example (SQL resource)**:

```yaml
- route:
    id: employee-data-resource
    autoStartup: false
    from:
      uri: sql:SELECT * FROM employees WHERE department = 'Engineering'
      steps:
        - marshal:
            json: {}  # Convert result to JSON string
```

The marshaling step ensures the data is a JSON string, which the capability can return to the AI agent.

## Complete Examples

### Example 1: Automatic Mapping (Minimal Configuration)

**Routes YAML**:

```yaml
- route:
    id: get-employee
    from:
      uri: direct:get-employee
      steps:
        - log:
            message: "Fetching employee ${header.Wanaku.employeeId}"
        - toD: "https://api.example.com/employees/${header.Wanaku.employeeId}"
```

**Rules YAML**:

```yaml
mcp:
  tools:
    - get-employee-info:
        route:
          id: "get-employee"
        description: "Retrieve employee information by ID"
```

**Result**: All parameters are mapped to headers with `Wanaku.` prefix.

### Example 2: Explicit Mapping with Multiple Parameters

**Routes YAML**:

```yaml
- route:
    id: update-inventory
    from:
      uri: direct:update-inventory
      steps:
        - log:
            message: "Updating inventory: SKU=${header.SKU}, Quantity=${header.QUANTITY}"
        - setHeader:
            name: CamelHttpMethod
            constant: POST
        - toD: "https://inventory-api.example.com/items/${header.SKU}?quantity=${header.QUANTITY}"
```

**Rules YAML**:

```yaml
mcp:
  tools:
    - update-inventory-quantity:
        route:
          id: "update-inventory"
        description: "Update the quantity of an inventory item"
        namespace: inventory-operations
        properties:
          - name: sku
            type: string
            description: The SKU of the item to update
            required: true
            mapping:
              type: header
              name: SKU
          - name: quantity
            type: int
            description: The new quantity value
            required: true
            mapping:
              type: header
              name: QUANTITY
```

**Result**: Only `sku` and `quantity` are mapped. Headers are named `SKU` and `QUANTITY`.

### Example 3: Tool and Resource in One File

**Routes YAML**:

```yaml
- route:
    id: send-email
    from:
      uri: direct:send-email
      steps:
        - log:
            message: "Sending email to ${header.Wanaku.recipient}"
        - toD: "smtp://mail.example.com?to=${header.Wanaku.recipient}"

- route:
    id: email-templates
    autoStartup: false
    from:
      uri: file:/data/email-templates?noop=true
      steps:
        - convertBodyTo:
            type: String
```

**Rules YAML**:

```yaml
mcp:
  tools:
    - send-notification-email:
        route:
          id: "send-email"
        description: "Send a notification email to a recipient"
        namespace: notifications

  resources:
    - email-templates:
        route:
          id: "email-templates"
        description: "Available email templates for notifications"
        namespace: notifications
```

**Result**:

- Tool: `send-notification-email` (invocable, sends email)
- Resource: `email-templates` (passive, AI agents can query available templates)

### Example 4: Multiple Tools Mapping to the Same Route

**Routes YAML**:

```yaml
- route:
    id: query-database
    from:
      uri: direct:query-database
      steps:
        - toD: "sql:${header.SQL_QUERY}"
```

**Rules YAML**:

```yaml
mcp:
  tools:
    - get-active-employees:
        route:
          id: "query-database"
        description: "Get all active employees"
        properties:
          - name: department
            type: string
            description: Filter by department
            required: false
            mapping:
              type: header
              name: DEPARTMENT

    - get-employee-count:
        route:
          id: "query-database"
        description: "Count employees in a department"
        properties:
          - name: department
            type: string
            description: Department to count
            required: true
            mapping:
              type: header
              name: DEPARTMENT
```

**Result**: Two tools, same route, different parameter mappings. Each tool can pre-configure the SQL query differently (e.g., via headers or route logic).

## Validation and Error Handling

### Route ID Not Found

If `route.id` doesn't match any loaded route:

```
ERROR Route 'unknown-route-id' not found
```

The tool is registered with Wanaku but invocations will fail. Verify:

- Route ID is spelled correctly (case-sensitive)
- Route loaded successfully (check logs for route loading errors)

### Missing Required Field

If a required field is missing:

```
ERROR Tool 'my-tool' is missing required field 'description'
```

Fix by adding the missing field.

### Invalid YAML Syntax

If the rules YAML is malformed:

```
ERROR Failed to parse rules file: expected <block end>, but found '<scalar>'
```

Use a YAML linter to validate syntax:

```bash
yamllint rules.yaml
```

### Resource Route with autoStartup Enabled

If a resource route has `autoStartup: true` (or defaults to true):

```
WARN Resource route 'my-resource-route' has auto-start enabled. This may cause unexpected behavior.
```

The service may log a warning but won't fail. However, the route will start consuming data at startup, which is usually not what you want for resources.

**Fix**: Add `autoStartup: false` to the route definition.

## Best Practices

### Tool Naming

- Use action verbs: `get-employee-info`, `update-inventory`, `send-email`
- Be specific: `get-active-employees` is clearer than `get-employees`
- Use kebab-case: `send-notification-email` (not `sendNotificationEmail` or `send_notification_email`)

### Descriptions

- Write for AI agents, not humans. AI agents parse descriptions to decide when to use tools.
- Be concise but complete: "Retrieve employee information including name, title, and department"
- Mention side effects: "Sends a confirmation email after updating the record"
- Avoid implementation details: Don't say "Calls the /api/v1/employees endpoint via HTTP GET"

### Namespaces

- Group related tools: All HR tools in `hr-operations`, all inventory tools in `inventory`
- Use consistent naming: `hr-operations`, `hr-data` (not `hr`, `HR-Ops`, `humanResources`)
- Don't over-namespace: If you only have 3 tools, a single namespace is fine

### Parameter Definitions

- Start with automatic mapping (no `properties`)
- Switch to explicit mapping when you need validation or custom header names
- Document every parameter: AI agents rely on descriptions to understand what to send
- Mark parameters as `required: true` if they're mandatory
- Use descriptive parameter names: `employeeId` is clearer than `id`

### Resource Routes

- Always set `autoStartup: false`
- Ensure data is convertible to String (use marshaling if needed)
- Use resources for read-only data sources (databases, files, APIs)
- Don't use resources for operations that modify state (use tools instead)

### Testing Your Rules

Before deploying to production:

1. **Validate YAML syntax**:
   ```bash
   yamllint rules.yaml
   ```

2. **Check route IDs match**:
   ```bash
   grep "id:" routes.camel.yaml
   grep "id:" rules.yaml
   ```

3. **Test with fail-fast enabled**:
   ```bash
   --fail-fast=true
   ```

   This catches errors early (e.g., missing routes, invalid syntax).

4. **Invoke each tool manually** (via Wanaku's API or a test script) to verify parameter mapping works as expected.

# Service Catalog Example

This example demonstrates how to package routes and dependencies as a **service catalog** -- the recommended approach for production deployments.

## What Is a Service Catalog?

A service catalog is a versioned, self-contained archive (ZIP file) that bundles:

- Camel routes (including `ai-tool:` routes for MCP tools)
- Maven dependencies

Service catalogs provide:

- **Version control**: Track catalog versions independently from the application
- **Atomic deployment**: Deploy all related routes and dependencies together
- **Simplified configuration**: Reference by name instead of individual file URLs
- **Multi-service support**: Package multiple related services in one catalog

## Files

- **index.properties**: Catalog metadata and service definitions
- **employee-system/routes.camel.yaml**: Camel routes for employee data operations
- **employee-system/dependencies.txt**: Required Camel components

## Catalog Structure

```text
employee-system-v2/
├── index.properties                      # Catalog metadata
└── employee-system/                      # Service directory
    ├── routes.camel.yaml                 # Camel routes (including ai-tool: routes)
    └── dependencies.txt                  # Maven dependencies
```

### index.properties Format

```properties
# Catalog name (used for versioning)
catalog.name=employee-system-v2

# List of services in this catalog (comma-separated)
catalog.services=employee-system

# Route file for each service
catalog.routes.employee-system=employee-system/routes.camel.yaml

# Dependencies file for each service
catalog.dependencies.employee-system=employee-system/dependencies.txt
```

## Creating a Service Catalog

### 1. Prepare the Directory Structure

```bash
mkdir -p my-catalog/my-service
```

### 2. Add Your Files

Place routes and dependencies in the service directory. Use the `ai-tool:` URI format for routes you want exposed as MCP tools.

### 3. Create index.properties

```bash
cat > my-catalog/index.properties << 'EOF'
catalog.name=my-catalog-v1
catalog.services=my-service
catalog.routes.my-service=my-service/routes.camel.yaml
catalog.dependencies.my-service=my-service/dependencies.txt
EOF
```

### 4. Package as ZIP

```bash
cd my-catalog
zip -r my-catalog-v1.zip *
```

## Running This Example

### 1. Package the Catalog

```bash
cd examples/service-catalog
zip -r employee-system-v2.zip *
```

### 2. Deploy Locally

```bash
java -jar ../../camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --service-catalog file://$(pwd)/employee-system-v2.zip \
  --service-catalog-system employee-system
```

### 3. Test via AI Agent

```text
AI: Get employee information for employee ID 12345
```

## Comparison: Individual Files vs Service Catalog

| Aspect | Individual Files | Service Catalog |
|--------|-----------------|-----------------|
| **Configuration** | Separate `--routes-ref`, `--dependencies` | Single `--service-catalog` + `--service-catalog-system` |
| **Versioning** | Manual (file names) | Built-in (`catalog.name`) |
| **Atomic deployment** | No (files can drift) | Yes (all files in one archive) |
| **Multi-service** | Requires multiple instances | Single catalog, select service |
| **Recommended for** | Development, testing | Production, staging |

## Next Steps

- Review the [Service Catalog Guide](../../docs/service-catalog-guide.md) for detailed catalog documentation
- Learn about [DataStore integration](../../docs/usage.md) for centralized catalog management
- Explore multi-service catalog patterns for enterprise deployments

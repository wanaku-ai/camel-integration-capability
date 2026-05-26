# Service Catalog Example

This example demonstrates how to package routes, rules, and dependencies as a **service catalog** — the recommended approach for production deployments.

## What Is a Service Catalog?

A service catalog is a versioned, self-contained archive (ZIP file) that bundles:

- Camel routes
- MCP tool/resource exposure rules
- Maven dependencies
- Metadata (catalog name, service names)

Service catalogs provide:

- **Version control**: Track catalog versions independently from the application
- **Atomic deployment**: Deploy all related routes and rules together
- **Simplified configuration**: Reference by name instead of individual file URLs
- **Multi-service support**: Package multiple related services in one catalog

## Files

- **index.properties**: Catalog metadata and service definitions
- **employee-system/routes.camel.yaml**: Camel routes for employee data operations
- **employee-system/rules.wanaku-rules.yaml**: MCP tool configuration
- **employee-system/dependencies.txt**: Required Camel components

## Catalog Structure

```text
employee-system-v2/
├── index.properties                      # Catalog metadata
└── employee-system/                      # Service directory
    ├── routes.camel.yaml                 # Camel routes
    ├── rules.wanaku-rules.yaml          # MCP rules
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

# Rules file for each service
catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml

# Dependencies file for each service
catalog.dependencies.employee-system=employee-system/dependencies.txt
```

**Key points:**

- `catalog.name`: Unique identifier for this catalog version
- `catalog.services`: List of service names (can be multiple, comma-separated)
- For each service, specify routes, rules, and dependencies using `catalog.<type>.<service-name>=<path>`

## Creating a Service Catalog

### 1. Prepare the Directory Structure

```bash
mkdir -p my-catalog/my-service
```

### 2. Add Your Files

Place routes, rules, and dependencies in the service directory:

```bash
cp routes.camel.yaml my-catalog/my-service/
cp rules.wanaku-rules.yaml my-catalog/my-service/
cp dependencies.txt my-catalog/my-service/
```

### 3. Create index.properties

```bash
cat > my-catalog/index.properties << 'EOF'
catalog.name=my-catalog-v1
catalog.services=my-service
catalog.routes.my-service=my-service/routes.camel.yaml
catalog.rules.my-service=my-service/rules.wanaku-rules.yaml
catalog.dependencies.my-service=my-service/dependencies.txt
EOF
```

### 4. Package as ZIP

```bash
cd my-catalog
zip -r my-catalog-v1.zip *
```

### 5. Upload to DataStore or File Server

Upload the ZIP file to:

- Wanaku DataStore service
- File server (accessible via HTTP/HTTPS)
- Cloud storage (S3, GCS, Azure Blob)
- Local filesystem

## Using a Service Catalog

### From DataStore

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system \
  --client-id wanaku-service \
  --client-secret your-secret
```

The application will:

1. Query the Wanaku DataStore for `employee-system-v2`
2. Download and extract the catalog
3. Load routes, rules, and dependencies for the `employee-system` service

### From HTTP/HTTPS

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --service-catalog https://example.com/catalogs/employee-system-v2.zip \
  --service-catalog-system employee-system \
  --client-id wanaku-service \
  --client-secret your-secret
```

### From Local Filesystem

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --service-catalog file:///path/to/employee-system-v2.zip \
  --service-catalog-system employee-system \
  --client-id wanaku-service \
  --client-secret your-secret
```

## Multi-Service Catalogs

A catalog can contain multiple services:

```properties
catalog.name=enterprise-integrations-v3
catalog.services=employee-system,inventory-system,crm-system

# Employee service
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt

# Inventory service
catalog.routes.inventory-system=inventory-system/routes.camel.yaml
catalog.rules.inventory-system=inventory-system/rules.wanaku-rules.yaml
catalog.dependencies.inventory-system=inventory-system/dependencies.txt

# CRM service
catalog.routes.crm-system=crm-system/routes.camel.yaml
catalog.rules.crm-system=crm-system/rules.wanaku-rules.yaml
catalog.dependencies.crm-system=crm-system/dependencies.txt
```

Deploy a specific service:

```bash
--service-catalog enterprise-integrations-v3 \
--service-catalog-system inventory-system
```

## Versioning Strategy

Best practices for catalog versioning:

- **Semantic versioning**: `catalog.name=my-catalog-v1.2.3`
- **Date-based**: `catalog.name=my-catalog-2026-04-15`
- **Feature-based**: `catalog.name=my-catalog-oauth-support`

Always include a version identifier in the catalog name to enable rollbacks and parallel deployments.

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
  --registration-announce-address localhost \
  --service-catalog file://$(pwd)/employee-system-v2.zip \
  --service-catalog-system employee-system \
  --client-id wanaku-service \
  --client-secret your-secret
```

### 3. Test via AI Agent

```text
AI: Get employee information for employee ID 12345
```

## Comparison: Individual Files vs Service Catalog

| Aspect | Individual Files | Service Catalog |
|--------|-----------------|-----------------|
| **Configuration** | Separate `--routes-ref`, `--rules-ref`, `--dependencies` | Single `--service-catalog` + `--service-catalog-system` |
| **Versioning** | Manual (file names) | Built-in (`catalog.name`) |
| **Atomic deployment** | No (files can drift) | Yes (all files in one archive) |
| **Multi-service** | Requires multiple instances | Single catalog, select service |
| **Recommended for** | Development, testing | Production, staging |

## Next Steps

- Review the [Migration Guide](../../docs/migration-guide.md#service-catalogs-recommended-approach) for upgrading from individual files
- Learn about [DataStore integration](../../docs/usage.md) for centralized catalog management
- Explore multi-service catalog patterns for enterprise deployments

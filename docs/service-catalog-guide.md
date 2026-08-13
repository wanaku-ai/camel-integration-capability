# Service Catalog Guide

Service catalogs are the recommended way to package and distribute Apache Camel routes for the Camel Integration Capability. Instead of managing individual files for routes and dependencies, a catalog bundles everything into a single versioned artifact.

## What is a Service Catalog?

A service catalog is a versioned ZIP archive stored in Wanaku's DataStore. It packages together:

- **Routes**: Apache Camel route definitions (YAML), including `ai-tool:` routes that define MCP tools
- **Dependencies**: External JARs needed by your routes (optional)

One catalog can contain resources for multiple systems. Each system gets its own subdirectory within the ZIP.

## Why Use Service Catalogs?

Service catalogs solve real operational headaches:

**Atomic Updates**: When you change a route's logic, you often need to update both the route and its dependencies. With individual files, there's a window where the service might pull the new route but old dependencies. Catalogs eliminate that.

**Version Control**: Production deployments need reproducibility. `employee-system-v2` means exactly that version of routes and dependencies, every time.

**Simplified Configuration**: Compare these two deployment commands:

**Without catalog** (multiple references):

```bash
--routes-ref datastore://routes.camel.yaml \
--dependencies datastore://deps.txt
```

**With catalog** (one reference):

```bash
--service-catalog employee-system-v2 \
--service-catalog-system employee-system
```

## Catalog Structure

A service catalog ZIP must contain an `index.properties` file at the root. This file maps system names to their resources within the archive.

### Example Catalog Layout

```text
employee-system-v2.zip
├── index.properties
└── employee-system/
    ├── routes.camel.yaml
    └── dependencies.txt
```

You can include multiple systems in one catalog:

```text
hr-systems-v3.zip
├── index.properties
├── employee-system/
│   ├── routes.camel.yaml
│   └── dependencies.txt
└── payroll-system/
    ├── routes.camel.yaml
    └── dependencies.txt
```

### index.properties Schema

The `index.properties` file defines the catalog's metadata and maps each system to its resources.

**Minimal example (single system):**

```properties
catalog.name=employee-system-v2
catalog.services=employee-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
```

**With dependencies:**

```properties
catalog.name=employee-system-v2
catalog.services=employee-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
```

**Multiple systems:**

```properties
catalog.name=hr-systems-v3
catalog.services=employee-system,payroll-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
catalog.routes.payroll-system=payroll-system/routes.camel.yaml
catalog.dependencies.payroll-system=payroll-system/dependencies.txt
```

#### Property Reference

| Property | Required | Description |
|----------|----------|-------------|
| `catalog.name` | Yes | Unique identifier for this catalog. Must match the value passed to `--service-catalog`. |
| `catalog.services` | Yes | Comma-separated list of system names included in this catalog. |
| `catalog.routes.<system>` | Yes | Path within the ZIP to the Camel routes YAML for `<system>`. |
| `catalog.dependencies.<system>` | No | Path within the ZIP to the dependencies file for `<system>`. Omit if no external dependencies are needed. |

**Important constraints:**

- All paths in `index.properties` are relative to the root of the ZIP archive.
- The paths must match the actual file locations inside the ZIP. Case-sensitive.
- `catalog.name` is used as the catalog identifier when running the service with `--service-catalog`.
- Each system listed in `catalog.services` must have at least `catalog.routes.<system>` defined.

## Creating a Service Catalog

### Step 1: Organize Your Files

Create a directory structure matching the catalog layout:

```bash
mkdir -p my-catalog/employee-system
cd my-catalog
```

Place your routes and dependencies in the system directory. Routes should use the `ai-tool:` URI format for tools you want exposed as MCP tools:

```yaml
# employee-system/routes.camel.yaml
- route:
    id: get-employee-info
    from:
      uri: ai-tool:get-employee-info
      parameters:
        description: "Fetches core profile data for a specific employee"
      steps:
        - toD: https://api.example.com/employees/${header.employeeId}
```

### Step 2: Create index.properties

In the `my-catalog` directory, create `index.properties`:

```properties
catalog.name=employee-system-v2
catalog.services=employee-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
```

### Step 3: Package the Catalog

```bash
zip -r employee-system-v2.zip index.properties employee-system/
```

**Verify the structure:**

```bash
unzip -l employee-system-v2.zip
```

If `index.properties` is nested inside a subdirectory instead of at the root, the catalog is broken.

## Publishing a Service Catalog

Upload the catalog ZIP to Wanaku's DataStore:

**Using Wanaku's DataStore API:**

```bash
curl -X POST http://wanaku-datastore:8080/api/v1/catalogs \
  -H "Content-Type: multipart/form-data" \
  -F "file=@employee-system-v2.zip"
```

**Using the Wanaku CLI (if available):**

```bash
wanaku datastore upload employee-system-v2.zip --type catalog
```

## Using a Service Catalog

### CLI Deployment

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://wanaku-server:8080 \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
```

The capability will:

1. Download `employee-system-v2.zip` from the DataStore
2. Extract the files for `employee-system` (routes, dependencies)
3. Load the routes and start the MCP server

If the download fails, the capability retries with exponential backoff (up to `--retries`, default 12).

### Kubernetes Deployment

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: Wanaku
metadata:
  name: wanaku-dev
spec:
  router:
    image: quay.io/wanaku/wanaku-router-backend:latest
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

## Versioning Best Practices

Service catalogs are immutable artifacts. Once deployed, `employee-system-v2` should always reference the same routes and dependencies.

### Recommended Naming Convention

- `employee-system-v2` -- version 2 of the employee system catalog
- `payroll-system-2026-01` -- payroll catalog for January 2026 release
- `hr-systems-prod-2.3.1` -- HR systems catalog, production version 2.3.1

Avoid generic names like `latest` or `current`.

### Version Lifecycle

**Development**: Create a catalog with a `-dev` or `-snapshot` suffix. Can be overwritten during active development.

**Staging**: Promote to a release candidate (`v3-rc1`). Should not be modified.

**Production**: Finalize the version (`v3`). Immutable. Future changes require `v4`.

## Catalog vs Individual File References

| Approach | When to Use |
|----------|-------------|
| **Service Catalog** | Production deployments, versioned releases, managing multiple systems |
| **Individual Files** | Development, prototyping, debugging a single route |

### Using Individual Files (Development Mode)

During development, it's often easier to iterate on a single route file:

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --routes-ref file:///workspace/routes.camel.yaml
```

**Important constraint**: `--service-catalog` is mutually exclusive with `--routes-ref` and `--dependencies`.

## Troubleshooting

### Catalog Download Failures

**Symptom**: Logs show repeated download attempts, eventually timing out.

**Check**:

1. Verify the catalog exists in Wanaku's DataStore
2. Verify `catalog.name` in `index.properties` matches `--service-catalog`
3. Check network connectivity

### System Not Found in Catalog

**Symptom**: Error like `System 'employee-system' not found in catalog 'hr-systems-v3'`.

**Fix**: Verify the system name is listed in `catalog.services` in `index.properties` (case-sensitive).

### Missing Files in Extracted Catalog

**Symptom**: `FileNotFoundException` for a routes or dependencies file.

**Cause**: The paths in `index.properties` don't match the actual file locations in the ZIP.

**Debug**: List the ZIP contents and compare to `index.properties`.

## Advanced: Multi-System Catalogs

Large organizations often manage multiple related systems. Multi-system catalogs let you version them together.

**Deploying multiple systems:**

Each capability instance references one system from the catalog:

```yaml
capabilities:
  - name: employee-system
    type: camel-integration-capability
    image: quay.io/wanaku/camel-integration-capability:latest
    env:
      - name: SERVICE_CATALOG
        value: "hr-systems-v3"
      - name: SERVICE_CATALOG_SYSTEM
        value: "employee-system"

  - name: payroll-system
    type: camel-integration-capability
    image: quay.io/wanaku/camel-integration-capability:latest
    env:
      - name: SERVICE_CATALOG
        value: "hr-systems-v3"
      - name: SERVICE_CATALOG_SYSTEM
        value: "payroll-system"
```

All services pull the same catalog ZIP but extract different systems.

# Service Catalog Guide

Service catalogs are the recommended way to package and distribute Apache Camel routes for the Camel Integration Capability. Instead of managing individual files for routes, rules, and dependencies, a catalog bundles everything into a single versioned artifact.

## What is a Service Catalog?

A service catalog is a versioned ZIP archive stored in Wanaku's DataStore. It packages together:

- **Routes**: Apache Camel route definitions (YAML)
- **Rules**: Route exposure rules defining which routes become MCP tools or resources
- **Dependencies**: External JARs needed by your routes (optional)

One catalog can contain resources for multiple systems. Each system gets its own subdirectory within the ZIP.

## Why Use Service Catalogs?

Service catalogs solve real operational headaches:

**Atomic Updates**: When you change a route's signature, you often need to update both the route logic and the exposure rules. With individual files, there's a window where the service might pull the new route but old rules. Catalogs eliminate that — everything updates together.

**Version Control**: Production deployments need reproducibility. `employee-system-v2` means exactly that version of routes, rules, and dependencies, every time. No hunting through git history to figure out which files match which deployment.

**Simplified Configuration**: Compare these two deployment commands:

**Without catalog** (three moving parts):

```bash
--routes-ref datastore://routes.camel.yaml \
--rules-ref datastore://rules.yaml \
--dependencies datastore://deps.txt
```

**With catalog** (one reference):

```bash
--service-catalog employee-system-v2 \
--service-catalog-system employee-system
```

The catalog approach scales better when you're managing multiple environments or multiple systems.

## Catalog Structure

A service catalog ZIP must contain an `index.properties` file at the root. This file maps system names to their resources within the archive.

### Example Catalog Layout

```text
employee-system-v2.zip
├── index.properties
└── employee-system/
    ├── routes.camel.yaml
    ├── rules.wanaku-rules.yaml
    └── dependencies.txt
```

You can include multiple systems in one catalog:

```text
hr-systems-v3.zip
├── index.properties
├── employee-system/
│   ├── routes.camel.yaml
│   ├── rules.wanaku-rules.yaml
│   └── dependencies.txt
└── payroll-system/
    ├── routes.camel.yaml
    ├── rules.wanaku-rules.yaml
    └── dependencies.txt
```

### index.properties Schema

The `index.properties` file defines the catalog's metadata and maps each system to its resources.

**Minimal example (single system):**

```properties
catalog.name=employee-system-v2
catalog.services=employee-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml
```

**With dependencies:**

```properties
catalog.name=employee-system-v2
catalog.services=employee-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
```

**Multiple systems:**

```properties
catalog.name=hr-systems-v3
catalog.services=employee-system,payroll-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
catalog.routes.payroll-system=payroll-system/routes.camel.yaml
catalog.rules.payroll-system=payroll-system/rules.wanaku-rules.yaml
catalog.dependencies.payroll-system=payroll-system/dependencies.txt
```

#### Property Reference

| Property | Required | Description |
|----------|----------|-------------|
| `catalog.name` | Yes | Unique identifier for this catalog. Must match the value passed to `--service-catalog`. |
| `catalog.services` | Yes | Comma-separated list of system names included in this catalog. |
| `catalog.routes.<system>` | Yes | Path within the ZIP to the Camel routes YAML for `<system>`. |
| `catalog.rules.<system>` | Yes | Path within the ZIP to the exposure rules YAML for `<system>`. |
| `catalog.dependencies.<system>` | No | Path within the ZIP to the dependencies file for `<system>`. Omit if no external dependencies are needed. |

**Important constraints:**

- All paths in `index.properties` are relative to the root of the ZIP archive.
- The paths must match the actual file locations inside the ZIP. Case-sensitive.
- `catalog.name` is used as the catalog identifier when running the service with `--service-catalog`.
- Each system listed in `catalog.services` must have at least `catalog.routes.<system>` and `catalog.rules.<system>` defined.

## Creating a Service Catalog

### Step 1: Organize Your Files

Create a directory structure matching the catalog layout:

```bash
mkdir -p my-catalog/employee-system
cd my-catalog
```

Place your routes, rules, and dependencies in the system directory:

```bash
# Example files
employee-system/routes.camel.yaml         # Camel routes
employee-system/rules.wanaku-rules.yaml   # Exposure rules
employee-system/dependencies.txt          # Dependencies (optional)
```

### Step 2: Create index.properties

In the `my-catalog` directory, create `index.properties`:

```properties
catalog.name=employee-system-v2
catalog.services=employee-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
```

Verify the paths. If `rules.wanaku-rules.yaml` is actually named `exposure-rules.yaml` in your directory, the catalog won't work. The property value must match the real filename.

### Step 3: Package the Catalog

Use the `zip` command to create the archive:

```bash
zip -r employee-system-v2.zip index.properties employee-system/
```

**What this does:**

- Creates `employee-system-v2.zip` containing `index.properties` and the `employee-system/` directory.
- The `-r` flag ensures subdirectories are included recursively.

**Verify the structure:**

```bash
unzip -l employee-system-v2.zip
```

Expected output:

```text
Archive:  employee-system-v2.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
      243  2026-01-15 14:32   index.properties
        0  2026-01-15 14:30   employee-system/
     1842  2026-01-15 14:30   employee-system/routes.camel.yaml
      567  2026-01-15 14:31   employee-system/rules.wanaku-rules.yaml
       89  2026-01-15 14:31   employee-system/dependencies.txt
---------                     -------
     2741                     5 files
```

If `index.properties` is nested inside a subdirectory (e.g., `my-catalog/index.properties` instead of `index.properties`), the catalog is broken. The capability expects `index.properties` at the root of the ZIP.

## Publishing a Service Catalog

Upload the catalog ZIP to Wanaku's DataStore. The exact mechanism depends on your Wanaku deployment, but typically:

**Using Wanaku's DataStore API:**

```bash
curl -X POST http://wanaku-datastore:8080/api/v1/catalogs \
  -H "Content-Type: multipart/form-data" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@employee-system-v2.zip"
```

**Using the Wanaku CLI (if available):**

```bash
wanaku datastore upload employee-system-v2.zip --type catalog
```

After uploading, the catalog is referenced by its `catalog.name` from `index.properties` — in this case, `employee-system-v2`.

## Using a Service Catalog

Once published, reference the catalog when starting the Camel Integration Capability.

### CLI Deployment

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://wanaku-router:8080 \
  --registration-announce-address my-service.example.com \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system \
  --client-id wanaku-service \
  --client-secret your-secret
```

**Parameters:**

- `--service-catalog`: The `catalog.name` from `index.properties`
- `--service-catalog-system`: The system name within the catalog (must be listed in `catalog.services`)

The capability will:

1. Register with the Wanaku router
2. Download `employee-system-v2.zip` from the DataStore
3. Extract the files for `employee-system` (routes, rules, dependencies)
4. Load the routes and start serving them as MCP tools

If the download fails, the capability retries with exponential backoff (up to `--retries`, default 12).

### Kubernetes Deployment

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: Wanaku
metadata:
  name: wanaku-dev
spec:
  auth:
    authServer: http://keycloak:8543
    authProxy: "auto"
  router:
    image: quay.io/wanaku/wanaku-router-backend:latest
  secrets:
    oidcCredentialsSecret: wanaku-credentials
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

The Wanaku operator injects `REGISTRATION_URL`, `CLIENT_ID`, and `CLIENT_SECRET` automatically.

## Versioning Best Practices

Service catalogs are immutable artifacts. Once deployed, `employee-system-v2` should always reference the same routes, rules, and dependencies.

### Recommended Naming Convention

Use descriptive names with version suffixes:

- `employee-system-v2` — version 2 of the employee system catalog
- `payroll-system-2026-01` — payroll catalog for January 2026 release
- `hr-systems-prod-2.3.1` — HR systems catalog, production version 2.3.1

Avoid generic names like `latest` or `current` — these defeat the purpose of versioning.

### Version Lifecycle

**Development**: Create a catalog with a `-dev` or `-snapshot` suffix:

```properties
catalog.name=employee-system-v3-dev
```

Deploy and test it in a development environment. The catalog can be overwritten during active development.

**Staging**: Promote the catalog to a release candidate version:

```properties
catalog.name=employee-system-v3-rc1
```

Deploy to staging. This catalog should not be modified. If changes are needed, create `v3-rc2`.

**Production**: Finalize the version:

```properties
catalog.name=employee-system-v3
```

Deploy to production. This catalog is now immutable. Future changes require `v4`.

### When to Create a New Version

Create a new catalog version when:

- **Route signatures change**: Adding, removing, or renaming route parameters
- **Dependencies change**: Upgrading a library, adding a new component
- **Business logic changes**: Modifying route behavior in a non-trivial way

You don't need a new version for:

- **Comment updates** in YAML
- **Log message changes** (unless they're part of the tool's contract)
- **Non-functional refactors** that don't alter route behavior

## Catalog vs Individual File References

The Camel Integration Capability supports two modes:

| Approach | When to Use |
|----------|-------------|
| **Service Catalog** | Production deployments, versioned releases, managing multiple systems |
| **Individual Files** | Development, prototyping, debugging a single route |

### Using Individual Files (Development Mode)

During development, it's often easier to iterate on a single route file without packaging a full catalog:

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --routes-ref file:///workspace/routes.camel.yaml \
  --rules-ref file:///workspace/rules.yaml \
  --client-id wanaku-service \
  --client-secret test-secret
```

This mode allows you to edit `routes.camel.yaml` and restart the service without rebuilding a ZIP.

**Important constraint**: `--service-catalog` is mutually exclusive with `--routes-ref`, `--rules-ref`, and `--dependencies`. The service rejects configurations that mix both approaches.

### Transitioning to Catalogs

Once your routes stabilize, transition to catalogs for deployment:

1. Create the catalog directory structure
2. Copy your working routes, rules, and dependencies into it
3. Write `index.properties`
4. Package the ZIP
5. Upload to Wanaku's DataStore
6. Update your deployment to use `--service-catalog`

Catalogs are the path from "it works on my machine" to "it works the same way every time."

## Troubleshooting

### Catalog Download Failures

**Symptom**: Logs show repeated download attempts, eventually timing out.

**Check**:

1. Verify the catalog exists in Wanaku's DataStore:

   ```bash
   curl -H "Authorization: Bearer $TOKEN" \
     http://wanaku-datastore:8080/api/v1/catalogs/employee-system-v2
   ```

2. Verify the `catalog.name` in `index.properties` matches `--service-catalog`:

   ```bash
   unzip -p employee-system-v2.zip index.properties | grep catalog.name
   ```

3. Check OAuth2 token acquisition:
   - The service needs a valid token to download catalogs
   - Look for `Failed to acquire token` in logs
   - Verify `--client-id` and `--client-secret` are correct

4. Check network connectivity:

   ```bash
   kubectl exec deployment/camel-integration-capability -- \
     curl -v http://wanaku-datastore:8080/health
   ```

### System Not Found in Catalog

**Symptom**: Error like `System 'employee-system' not found in catalog 'hr-systems-v3'`.

**Cause**: The system name passed to `--service-catalog-system` isn't listed in `catalog.services` in `index.properties`.

**Fix**:

1. List the available systems:

   ```bash
   unzip -p hr-systems-v3.zip index.properties | grep catalog.services
   ```

2. Use the exact system name (case-sensitive):

   ```bash
   --service-catalog-system payroll-system
   ```

### Missing Files in Extracted Catalog

**Symptom**: `FileNotFoundException: employee-system/routes.camel.yaml not found`.

**Cause**: The paths in `index.properties` don't match the actual file locations in the ZIP.

**Debug**:

1. List the ZIP contents:

   ```bash
   unzip -l employee-system-v2.zip
   ```

2. Compare to `index.properties`:

   ```bash
   unzip -p employee-system-v2.zip index.properties
   ```

3. Paths are case-sensitive and must match exactly. If the ZIP contains `Employee-System/Routes.yaml` but `index.properties` says `employee-system/routes.camel.yaml`, it won't work.

### Malformed index.properties

**Symptom**: Parsing errors when loading the catalog.

**Common mistakes**:

- **Missing required properties**: Every system needs `catalog.routes.<system>` and `catalog.rules.<system>`.
- **Typos in system names**: `catalog.routes.employee-system` but `catalog.rules.employe-system` (missing 'e').
- **Wrong separator**: Use commas in `catalog.services`, not spaces or semicolons.
- **Special characters**: Avoid spaces or special characters in system names. Use `employee-system`, not `employee system`.

**Validate**:

```bash
unzip -p employee-system-v2.zip index.properties
```

Check that:

- `catalog.name` is present
- `catalog.services` lists all systems
- Each system has `catalog.routes.<system>` and `catalog.rules.<system>`

## Advanced: Multi-System Catalogs

Large organizations often manage multiple related systems. Multi-system catalogs let you version them together.

### Example: HR Systems Catalog

```text
hr-systems-v3.zip
├── index.properties
├── employee-system/
│   ├── routes.camel.yaml
│   ├── rules.wanaku-rules.yaml
│   └── dependencies.txt
├── payroll-system/
│   ├── routes.camel.yaml
│   └── rules.wanaku-rules.yaml
└── benefits-system/
    ├── routes.camel.yaml
    ├── rules.wanaku-rules.yaml
    └── dependencies.txt
```

**index.properties:**

```properties
catalog.name=hr-systems-v3
catalog.services=employee-system,payroll-system,benefits-system
catalog.routes.employee-system=employee-system/routes.camel.yaml
catalog.rules.employee-system=employee-system/rules.wanaku-rules.yaml
catalog.dependencies.employee-system=employee-system/dependencies.txt
catalog.routes.payroll-system=payroll-system/routes.camel.yaml
catalog.rules.payroll-system=payroll-system/rules.wanaku-rules.yaml
catalog.routes.benefits-system=benefits-system/routes.camel.yaml
catalog.rules.benefits-system=benefits-system/rules.wanaku-rules.yaml
catalog.dependencies.benefits-system=benefits-system/dependencies.txt
```

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

  - name: benefits-system
    type: camel-integration-capability
    image: quay.io/wanaku/camel-integration-capability:latest
    env:
      - name: SERVICE_CATALOG
        value: "hr-systems-v3"
      - name: SERVICE_CATALOG_SYSTEM
        value: "benefits-system"
```

All three services pull the same catalog ZIP but extract different systems. This ensures version alignment — if the employee and payroll systems share data models or integration contracts, they're guaranteed to be in sync.

**When to use multi-system catalogs:**

- Systems share dependencies or common integration logic
- You need atomic version updates across related systems
- Systems integrate with each other (e.g., employee promotion triggers payroll update)

**When to use separate catalogs:**

- Systems have independent release cycles
- Teams manage systems separately
- Systems rarely change together

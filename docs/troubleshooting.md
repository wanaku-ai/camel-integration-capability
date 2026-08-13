# Troubleshooting Guide

This guide walks through common issues when running the Camel Integration Capability and how to diagnose them.

## Routes Not Loading

**Symptom**: The service starts but doesn't expose any tools. Logs may show route loading failures.

### Check Route YAML Syntax

Invalid YAML syntax causes routes to fail silently (unless `--fail-fast` is enabled).

**Validate locally**:

```bash
yamllint routes.camel.yaml
```

**Common syntax errors**:

- **Incorrect indentation**: YAML requires consistent spaces (not tabs)
- **Missing colons**: `from` instead of `from:`
- **Unquoted special characters**: URIs with query parameters need quotes

Validate your routes using [Kaoto](https://kaoto.io) or [Apache Camel Karavan](https://camel.apache.org/karavan).

### Verify Route Reference URI

**Check the URI scheme**:

- `datastore://routes.camel.yaml` -- downloads from Wanaku's DataStore
- `file:///absolute/path/routes.camel.yaml` -- loads from local filesystem

**For file:// URIs**, verify the path is absolute and the file exists:

```bash
# Wrong (relative path)
--routes-ref file://routes.camel.yaml

# Correct (absolute path)
--routes-ref file:///tmp/routes.camel.yaml
```

### Check for Missing Dependencies

Routes that reference Camel components not in the base distribution will fail to load.

**Symptom**: Logs show `NoClassDefFoundError` or `Failed to create endpoint` errors.

**Fix**: Provide a dependencies file:

```text
org.apache.camel:camel-http:4.22.0
```

Reference it with `--dependencies`:

```bash
--dependencies datastore://dependencies.txt
```

**Common missing components**:

| Route uses | Dependency |
|------------|------------|
| `http://` or `https://` | `org.apache.camel:camel-http:4.22.0` |
| `sql:` | `org.apache.camel:camel-sql:4.22.0` |
| `kafka:` | `org.apache.camel:camel-kafka:4.22.0` |
| `jackson` marshaling | `org.apache.camel:camel-jackson:4.22.0` |

### Understand --fail-fast Behavior

By default, the service uses `--fail-fast=false`:

- If a route fails to load, the error is logged but the service continues
- Other routes in the same file may load successfully

**Enable fail-fast for debugging**:

```bash
--fail-fast=true
```

### Enable Debug Logging for Route Loading

Set the application logger to DEBUG:

```xml
<Logger name="ai.wanaku.capability.camel" level="DEBUG"/>
```

## MCP Server Issues

**Symptom**: The MCP server starts but tools are not accessible.

### Verify MCP Port

Check that `--mcp-port` (default 8080) is not already in use:

```bash
kubectl logs deployment/camel-integration-capability | grep -i "port\|bind\|mcp"
```

Look for:

```text
INFO  MCP server started on port 8080
```

Or:

```text
ERROR Failed to bind to port 8080: Address already in use
```

**Fix**: Change the port:

```bash
--mcp-port 9090
```

### Check ai-tool: Route Configuration

Tools are only exposed for routes using the `ai-tool:` URI format. Verify your routes use this format:

```yaml
- route:
    id: my-tool
    from:
      uri: ai-tool:my-tool
      parameters:
        description: "My tool description"
      steps:
        - log:
            message: "Tool invoked"
```

Routes using `direct:`, `timer:`, or other standard URIs are NOT exposed as MCP tools.

### Check MCP Tag Filtering

If `--mcp-tags` is set, only routes whose tags match are exposed. Verify:

1. Your routes have matching tags
2. The `--mcp-tags` value includes the tags your routes use

To expose all routes, omit `--mcp-tags`.

### In Kubernetes: Verify Service Configuration

The Service resource must target the MCP port:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: camel-integration-capability
spec:
  selector:
    app: camel-integration-capability
  ports:
    - name: mcp
      port: 8080
      targetPort: 8080
```

## Dependency Download Failures

**Symptom**: Routes using external libraries fail to load. Logs show Maven download errors.

### Verify Maven Coordinates

Dependencies must use the `groupId:artifactId:version` format:

**Correct**:

```text
org.apache.camel:camel-http:4.22.0
```

**Wrong**:

```text
camel-http:4.22.0              # Missing groupId
org.apache.camel:camel-http    # Missing version
```

### Check Repository Access

By default, the service downloads from Maven Central. For private repositories:

```bash
--repositories http://my-private-repo.com/maven,https://repo1.maven.org/maven2
```

### Retry Behavior

Dependency downloads retry with exponential backoff. Default settings:

- Max retries: 12
- Initial wait: 5 seconds

**Watch retry attempts**:

```bash
kubectl logs deployment/camel-integration-capability | grep -i "download\|retry"
```

### Check --data-dir Permissions

Downloaded dependencies are cached in `--data-dir` (default `/tmp`). Verify the directory is writable:

```bash
kubectl exec deployment/camel-integration-capability -- ls -ld /data
```

**Fix**: Use a writable volume:

```yaml
volumeMounts:
  - name: data
    mountPath: /data
volumes:
  - name: data
    emptyDir: {}
```

## Service Catalog Issues

**Symptom**: Using `--service-catalog` but the catalog doesn't download or fails to parse.

### Verify Catalog Exists in DataStore

Check the logs for download errors. Verify the catalog name matches `catalog.name` in `index.properties`.

### Check --service-catalog-system Matches index.properties

The system name must be listed in `catalog.services`:

```bash
unzip -p catalog.zip index.properties | grep catalog.services
```

### Service Catalogs are Mutually Exclusive with Individual References

You can't mix `--service-catalog` with `--routes-ref` or `--dependencies`.

## Debug Logging Configuration

### Key Loggers

| Logger | What It Shows |
|--------|---------------|
| `ai.wanaku.capability.camel.CamelToolMain` | Application startup, resource downloading |
| `ai.wanaku.capability.camel.WanakuCamelManager` | Route loading, MCP server configuration |
| `org.apache.camel` | Camel framework events (route starts, exchanges, errors) |

### Log4j2 Configuration

Create a `log4j2.xml` file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Logger name="ai.wanaku.capability.camel" level="DEBUG"/>
    <Logger name="org.apache.camel" level="INFO"/>
    <Root level="INFO">
      <AppenderRef ref="Console"/>
    </Root>
  </Loggers>
</Configuration>
```

**For local runs**:

```bash
-Dlog4j.configurationFile=/path/to/log4j2.xml
```

**In Kubernetes**, mount via ConfigMap:

```yaml
env:
  - name: JAVA_TOOL_OPTIONS
    value: "-Dlog4j.configurationFile=/config/log4j2.xml"
volumeMounts:
  - name: log-config
    mountPath: /config
volumes:
  - name: log-config
    configMap:
      name: log4j2-config
```

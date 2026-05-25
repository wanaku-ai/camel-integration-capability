# Troubleshooting Guide

This guide walks through common issues when running the Camel Integration Capability and how to diagnose them. Organized by symptom, not cause — start with what you're seeing in the logs or behavior.

## Routes Not Loading

**Symptom**: The service starts, registers with Wanaku, but doesn't expose any tools or resources. Logs may show route loading failures.

### Check Route YAML Syntax

Invalid YAML syntax causes routes to fail silently (unless `--fail-fast` is enabled).

**Validate locally**:

```bash
# Use yamllint or a YAML parser
yamllint routes.camel.yaml

# Or use Camel's validation (if you have Camel installed)
camel validate routes.camel.yaml
```

**Common syntax errors**:

- **Incorrect indentation**: YAML requires consistent spaces (not tabs)
- **Missing colons**: `from` instead of `from:`
- **Unquoted special characters**: URIs with query parameters need quotes

**Example of invalid YAML**:

```yaml
- route:
    id: example
    from:
    uri: direct:start  # Wrong indentation
```

**Correct version**:

```yaml
- route:
    id: example
    from:
      uri: direct:start
```

Validate your routes using [Kaoto](https://kaoto.io) or [Apache Camel Karavan](https://camel.apache.org/karavan) — both provide visual editors with built-in validation.

### Verify Route Reference URI

If the route file can't be found or downloaded, routes won't load.

**Check the URI scheme**:

- `datastore://routes.camel.yaml` — downloads from Wanaku's DataStore after registration
- `file:///absolute/path/routes.camel.yaml` — loads from local filesystem

**For datastore:// URIs**:

1. Verify the file exists in Wanaku's DataStore:
   ```bash
   curl -H "Authorization: Bearer $TOKEN" \
     http://wanaku-datastore:8080/api/v1/files/routes.camel.yaml
   ```

2. Check that OAuth2 authentication succeeded (DataStore requires a token).

3. Look for download retry attempts in logs:
   ```
   INFO  Attempting to download datastore://routes.camel.yaml (attempt 1/12)
   ERROR Failed to download routes.camel.yaml: 404 Not Found
   ```

**For file:// URIs**:

1. Verify the path is absolute, not relative:
   ```bash
   # Wrong (relative path)
   --routes-ref file://routes.camel.yaml

   # Correct (absolute path)
   --routes-ref file:///tmp/routes.camel.yaml
   ```

2. Check file permissions:
   ```bash
   ls -la /tmp/routes.camel.yaml
   ```

   The service must have read access. In Kubernetes, this often means the file is in a mounted ConfigMap or volume.

3. In Kubernetes, exec into the pod and verify:
   ```bash
   kubectl exec deployment/camel-integration-capability -- ls -la /data/routes.camel.yaml
   ```

### Check for Missing Dependencies

Routes that reference Camel components not in the base distribution will fail to load.

**Symptom**: Logs show `NoClassDefFoundError` or `Failed to create endpoint` errors.

**Example**:

```
ERROR Failed to create route: No component found with name 'http'
```

This means the route uses `camel-http` but it's not on the classpath.

**Fix**: Provide a dependencies file:

```
org.apache.camel:camel-http:4.18.1
```

Reference it with `--dependencies`:

```bash
--dependencies datastore://dependencies.txt
```

Or include it in your service catalog's `catalog.dependencies.<system>` property.

**Common missing components**:

| Route uses | Dependency |
|------------|------------|
| `http://` or `https://` | `org.apache.camel:camel-http:4.18.1` |
| `sql:` | `org.apache.camel:camel-sql:4.18.1` |
| `kafka:` | `org.apache.camel:camel-kafka:4.18.1` |
| `jackson` marshaling | `org.apache.camel:camel-jackson:4.18.1` |
| `aws2-s3:` | `org.apache.camel:camel-aws2-s3:4.18.1` |

Check the [Camel Components documentation](https://camel.apache.org/components/latest/) to find the correct Maven coordinates for your component.

### Understand --fail-fast Behavior

By default, the service uses `--fail-fast=false`. This means:

- If a route fails to load, the error is logged but **the service continues**
- Other routes in the same file may load successfully
- The service registers and serves the routes that *did* load

**Check how many routes loaded**:

```bash
kubectl logs deployment/camel-integration-capability | grep "Started route"
```

If you see:

```
INFO Started route-1 (direct://route-1)
INFO Started route-2 (direct://route-2)
```

But your rules expose three tools, one route didn't load.

**Enable fail-fast for debugging**:

```bash
--fail-fast=true
```

With this flag, the service shuts down immediately if any route fails to load. Useful for catching errors early in development.

### Enable Debug Logging for Route Loading

Set the `WanakuRoutesLoader` logger to DEBUG:

**Log4j2 configuration** (create or edit `log4j2.xml`):

```xml
<Configuration status="WARN">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Logger name="ai.wanaku.capability.camel.util.WanakuRoutesLoader" level="DEBUG"/>
    <Root level="INFO">
      <AppenderRef ref="Console"/>
    </Root>
  </Loggers>
</Configuration>
```

Place this file on the classpath or reference it:

```bash
-Dlog4j.configurationFile=/config/log4j2.xml
```

In Kubernetes, mount it via ConfigMap:

```yaml
volumeMounts:
  - name: log-config
    mountPath: /config
volumes:
  - name: log-config
    configMap:
      name: log4j2-config
```

**Debug output shows**:

- Which routes are being parsed
- Dependency resolution for each route
- Exact error messages when a route fails

## Authentication Failures

**Symptom**: Service fails to register, logs show OAuth2 token errors, or DataStore downloads fail with 401/403.

### Verify Client Credentials

Check `--client-id` and `--client-secret` match the OAuth2 provider's configuration.

**For Keycloak**:

1. Log into the Keycloak admin console
2. Navigate to your realm → Clients
3. Find the client ID (must match `--client-id`)
4. Go to the Credentials tab
5. Copy the client secret (must match `--client-secret`)

**Common mistake**: Using the wrong realm or client. If you have multiple Keycloak realms, ensure you're referencing the right one.

### Check Token Endpoint URL

The `--token-endpoint` must point to the OAuth2/OIDC token endpoint. For Keycloak, **include the realm path**:

**Wrong**:
```bash
--token-endpoint http://keycloak:8543/
```

**Correct**:
```bash
--token-endpoint http://keycloak:8543/realms/my-realm/
```

The service appends `/protocol/openid-connect/token` automatically, so the full URL becomes:

```
http://keycloak:8543/realms/my-realm/protocol/openid-connect/token
```

**Test token acquisition manually**:

```bash
curl -X POST http://keycloak:8543/realms/my-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=wanaku-service" \
  -d "client_secret=your-secret"
```

If this fails with 401, the credentials are wrong. If it returns a token, the service should work.

### When Authentication is Disabled

Some Wanaku deployments run without authentication (e.g., local development). In this case:

**Omit `--client-secret`**:

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --routes-ref file:///tmp/routes.camel.yaml \
  --client-id wanaku-service
```

The service will skip OAuth2 token acquisition if no secret is provided.

**Note**: `--client-id` is still required for service identification, even if authentication is disabled.

### Check Network Connectivity to OAuth2 Provider

If the token endpoint is unreachable, authentication will fail.

**Test from the pod**:

```bash
kubectl exec deployment/camel-integration-capability -- \
  curl -v http://keycloak:8543/realms/my-realm/
```

**Common issues**:

- **DNS resolution failure**: The service can't resolve `keycloak` hostname. Check Kubernetes DNS or use an IP address.
- **Firewall rules**: The pod's network policy blocks access to the OAuth2 provider.
- **Wrong port**: Keycloak default is 8080 (HTTP) or 8443 (HTTPS), not 8543. Verify your deployment.

### Token Refresh Errors

The service acquires a token at startup and refreshes it periodically. If refresh fails, requests to Wanaku will start returning 401.

**Check logs**:

```bash
kubectl logs deployment/camel-integration-capability | grep -i "token\|refresh"
```

Look for:

```
ERROR Failed to refresh OAuth2 token
ERROR HTTP 401 Unauthorized when calling Wanaku API
```

**Cause**: The token expired and refresh failed, usually due to:

- Client credentials changed in Keycloak
- Network issues between the service and the OAuth2 provider
- OAuth2 provider restarted and invalidated issued tokens

**Fix**: Restart the service to acquire a fresh token:

```bash
kubectl rollout restart deployment/camel-integration-capability
```

## Dependency Download Failures

**Symptom**: Routes using external libraries fail to load. Logs show Maven download errors.

### Verify Maven Coordinates

Dependencies must use the `groupId:artifactId:version` format:

**Correct**:
```
org.apache.camel:camel-http:4.18.1
com.mycompany:custom-beans:2.0.0
```

**Wrong**:
```
camel-http:4.18.1              # Missing groupId
org.apache.camel:camel-http    # Missing version
```

**Check your dependencies file**:

```bash
cat dependencies.txt
```

Ensure it's comma-separated (or newline-separated):

```
org.apache.camel:camel-http:4.18.1,org.apache.camel:camel-jackson:4.18.1
```

Or:

```
org.apache.camel:camel-http:4.18.1
org.apache.camel:camel-jackson:4.18.1
```

### Check Repository Access

By default, the service downloads from Maven Central. If you're using a private repository or a corporate mirror, specify it with `--repositories`:

```bash
--repositories http://my-private-repo.com/maven,https://repo1.maven.org/maven2
```

**Test repository access**:

```bash
curl -I http://my-private-repo.com/maven/org/apache/camel/camel-http/4.18.1/camel-http-4.18.1.jar
```

If it returns 404 or 403, the repository isn't accessible or the artifact doesn't exist there.

**For private repositories requiring authentication**, the service currently doesn't support authenticated Maven downloads. Workarounds:

1. Use a repository proxy (e.g., Nexus, Artifactory) with anonymous access
2. Pre-package dependencies in a custom Docker image
3. Use the plugin mode and include dependencies in your application's classpath

### Retry Behavior

Dependency downloads retry with exponential backoff. Default settings:

- Max retries: 12
- Initial wait: 5 seconds
- Exponential backoff multiplier: 2

**Watch retry attempts**:

```bash
kubectl logs deployment/camel-integration-capability | grep -i "download\|retry"
```

You'll see:

```
INFO  Attempting to download org.apache.camel:camel-http:4.18.1 (attempt 1/12)
WARN  Download failed, retrying in 5 seconds...
INFO  Attempting to download org.apache.camel:camel-http:4.18.1 (attempt 2/12)
```

If all retries fail, the service gives up. Check the final error message for the root cause.

### Verify Network Access to Maven Repositories

**Test from the pod**:

```bash
kubectl exec deployment/camel-integration-capability -- \
  curl -v https://repo1.maven.org/maven2/org/apache/camel/camel-http/4.18.1/camel-http-4.18.1.pom
```

If this times out or returns a connection error, the pod can't reach Maven Central. Possible causes:

- **No internet egress**: The Kubernetes cluster blocks outbound traffic
- **Proxy required**: Corporate networks often require HTTP proxies
- **DNS issues**: Can't resolve `repo1.maven.org`

**Fix for proxy environments**: Set Java proxy properties:

```bash
-Dhttp.proxyHost=proxy.company.com -Dhttp.proxyPort=8080 \
-Dhttps.proxyHost=proxy.company.com -Dhttps.proxyPort=8080
```

In Kubernetes, add to the container args or via `JAVA_TOOL_OPTIONS`:

```yaml
env:
  - name: JAVA_TOOL_OPTIONS
    value: "-Dhttp.proxyHost=proxy.company.com -Dhttp.proxyPort=8080"
```

### Check --data-dir Permissions

Downloaded dependencies are cached in `--data-dir` (default `/tmp`). If the service can't write to this directory, downloads fail.

**Check permissions**:

```bash
kubectl exec deployment/camel-integration-capability -- ls -ld /tmp
```

Ensure the directory is writable. In some Kubernetes security contexts, `/tmp` might be read-only.

**Fix**: Use a writable volume:

```yaml
volumeMounts:
  - name: data
    mountPath: /data
volumes:
  - name: data
    emptyDir: {}
```

Then:

```bash
--data-dir /data
```

## Service Not Registering

**Symptom**: The service starts, but never appears in Wanaku's service registry. AI agents can't invoke the tools.

### Verify Registration URL

The `--registration-url` must point to the Wanaku router's discovery service.

**Test connectivity**:

```bash
curl http://wanaku-router:8080/discovery/services
```

If this fails, the service can't reach the router.

**In Kubernetes**: Check that the service name resolves:

```bash
kubectl exec deployment/camel-integration-capability -- \
  nslookup wanaku-router
```

If DNS fails, verify:

- The router is running: `kubectl get pods -l app=wanaku-router`
- The service exists: `kubectl get svc wanaku-router`

### Check Registration Announce Address

The `--registration-announce-address` is the address the router uses to call back to the capability.

**Common mistake**: Using `localhost` in Kubernetes. The router can't reach `localhost` on another pod.

**Correct for Kubernetes**:

```bash
--registration-announce-address camel-integration-capability.default.svc.cluster.local
```

Or use the pod's IP (less reliable if the pod restarts):

```bash
--registration-announce-address $(hostname -i)
```

**For local development** (router and capability on the same machine):

```bash
--registration-announce-address localhost
```

**Test if the router can reach the announced address**:

From the router pod:

```bash
kubectl exec deployment/wanaku-router -- \
  curl http://camel-integration-capability:9190/health
```

If this fails, the router can't call back. Check:

- Firewall rules
- Network policies
- Service definitions

### Review Retry Settings

Registration retries up to `--retries` times (default 12), waiting `--wait-seconds` (default 5) between attempts.

**Watch retry attempts**:

```bash
kubectl logs deployment/camel-integration-capability | grep -i "registration\|retry"
```

You'll see:

```
INFO  Attempting to register with Wanaku (attempt 1/12)
WARN  Registration failed: Connection refused
INFO  Waiting 5 seconds before retry...
INFO  Attempting to register with Wanaku (attempt 2/12)
```

If it exhausts all retries, check the final error. Common causes:

- **Connection refused**: Router isn't running or wrong port
- **401 Unauthorized**: OAuth2 token acquisition failed
- **500 Internal Server Error**: Router has a bug or misconfiguration

### OAuth2 Token for Registration

Registration requires authentication. If token acquisition fails, registration fails.

**Check token acquisition**:

```bash
kubectl logs deployment/camel-integration-capability | grep -i "token\|oauth"
```

Look for:

```
INFO  Acquired OAuth2 token successfully
```

Or:

```
ERROR Failed to acquire OAuth2 token: invalid_client
```

If token acquisition fails, see the [Authentication Failures](#authentication-failures) section.

### gRPC Server Must Start Before Registration

The service starts a gRPC server on `--grpc-port` (default 9190) before attempting registration. If the port is already in use or binding fails, the pod won't register.

**Check gRPC server startup**:

```bash
kubectl logs deployment/camel-integration-capability | grep -i "grpc\|port\|bind"
```

Look for:

```
INFO  gRPC server started on port 9190
```

Or:

```
ERROR Failed to bind to port 9190: Address already in use
```

**If port is in use**: Change the port:

```bash
--grpc-port 9191
```

**In Kubernetes**: Ensure the Service definition matches the gRPC port:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: camel-integration-capability
spec:
  selector:
    app: camel-integration-capability
  ports:
    - name: grpc
      port: 9190
      targetPort: 9190
```

If `targetPort` doesn't match `--grpc-port`, the router can't reach the service.

## gRPC Connection Issues

**Symptom**: Wanaku registers the service, but tool invocations fail with connection errors.

### Default gRPC Port

The service listens on port 9190 by default. Verify `--grpc-port` matches what the router expects.

**Test the gRPC port**:

```bash
kubectl exec deployment/wanaku-router -- \
  grpcurl -plaintext camel-integration-capability:9190 list
```

This should list the available gRPC services:

```
ai.wanaku.capability.grpc.CamelToolService
ai.wanaku.capability.grpc.CamelResourceService
```

If it fails with `connection refused`, the port is wrong or the service isn't listening.

### Check Firewall and Security Group Rules

If the router and capability are in different networks, firewall rules might block gRPC traffic.

**In cloud environments** (AWS, GCP, Azure):

- Check security group rules allow ingress on port 9190
- Verify the capability's service is exposed to the router's network

**In Kubernetes**:

- Check NetworkPolicies aren't blocking traffic
- Verify the Service targets the correct pod port

### In Kubernetes: Verify Service Configuration

The Service resource must target the gRPC port:

```bash
kubectl get svc camel-integration-capability -o yaml
```

Look for:

```yaml
spec:
  ports:
    - name: grpc
      port: 9190
      targetPort: 9190
  selector:
    app: camel-integration-capability
```

**If the selector is wrong**, the Service won't route traffic to the pod. Fix:

```bash
kubectl label pod camel-integration-capability-... app=camel-integration-capability
```

### Test Connectivity with grpcurl

`grpcurl` is a command-line tool for testing gRPC services.

**Install grpcurl**:

```bash
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

**Test from the router pod**:

```bash
kubectl exec deployment/wanaku-router -- \
  grpcurl -plaintext camel-integration-capability:9190 \
  ai.wanaku.capability.grpc.CamelToolService/InvokeTool
```

If this hangs or fails, gRPC connectivity is broken.

## Service Catalog Issues

**Symptom**: Using `--service-catalog` but the catalog doesn't download or fails to parse.

### Verify Catalog Exists in DataStore

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://wanaku-datastore:8080/api/v1/catalogs/employee-system-v2
```

If it returns 404, the catalog wasn't uploaded or the name is wrong.

### Check --service-catalog-system Matches index.properties

The system name must be listed in `catalog.services`.

**Download the catalog manually and inspect it**:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://wanaku-datastore:8080/api/v1/catalogs/employee-system-v2 -o catalog.zip

unzip -p catalog.zip index.properties
```

Look for:

```properties
catalog.services=employee-system,payroll-system
```

If your `--service-catalog-system` is `hr-system` but the catalog only lists `employee-system` and `payroll-system`, it will fail.

### Service Catalogs are Mutually Exclusive with Individual References

You can't mix `--service-catalog` with `--routes-ref`, `--rules-ref`, or `--dependencies`.

**This will fail**:

```bash
--service-catalog employee-system-v2 \
--service-catalog-system employee-system \
--routes-ref datastore://routes.camel.yaml
```

**Error**:

```
--service-catalog is mutually exclusive with --routes-ref, --rules-ref, and --dependencies
```

**Fix**: Use one approach or the other, not both.

### Download Retries with Exponential Backoff

If catalog download fails, the service retries. Watch the logs:

```bash
kubectl logs deployment/camel-integration-capability | grep -i "catalog\|download"
```

You'll see:

```
INFO  Downloading service catalog 'employee-system-v2'
WARN  Download failed, retrying in 5 seconds...
INFO  Downloading service catalog 'employee-system-v2' (attempt 2/12)
```

If all retries fail, check:

- OAuth2 token is valid
- Catalog exists in DataStore
- Network connectivity to DataStore

## Debug Logging Configuration

When troubleshooting, enable detailed logging to see what's happening inside the service.

### Key Loggers

| Logger | What It Shows |
|--------|---------------|
| `ai.wanaku.capability.camel.CamelToolMain` | Application startup, registration, initialization |
| `ai.wanaku.capability.camel.grpc.CamelTool` | Tool invocations, parameter mapping, route execution |
| `ai.wanaku.capability.camel.util.WanakuRoutesLoader` | Route loading, YAML parsing, dependency resolution |
| `org.apache.camel` | Camel framework events (route starts, exchanges, errors) |
| `ai.wanaku.capabilities.sdk` | Wanaku SDK internals (registration, token acquisition, API calls) |

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
    <!-- Capability-specific loggers -->
    <Logger name="ai.wanaku.capability.camel" level="DEBUG"/>
    <Logger name="ai.wanaku.capabilities.sdk" level="DEBUG"/>
    
    <!-- Camel framework (verbose, use sparingly) -->
    <Logger name="org.apache.camel" level="INFO"/>
    
    <!-- Root logger -->
    <Root level="INFO">
      <AppenderRef ref="Console"/>
    </Root>
  </Loggers>
</Configuration>
```

**Mount in Kubernetes**:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: log4j2-config
data:
  log4j2.xml: |
    <?xml version="1.0" encoding="UTF-8"?>
    <Configuration status="WARN">
      <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
          <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
      </Appenders>
      <Loggers>
        <Logger name="ai.wanaku.capability.camel" level="DEBUG"/>
        <Root level="INFO">
          <AppenderRef ref="Console"/>
        </Root>
      </Loggers>
    </Configuration>
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-integration-capability
spec:
  template:
    spec:
      containers:
        - name: capability
          image: quay.io/wanaku/camel-integration-capability:latest
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

**For local runs**:

```bash
-Dlog4j.configurationFile=/path/to/log4j2.xml
```

### What Debug Logs Reveal

**Route loading**:

```
DEBUG WanakuRoutesLoader - Parsing routes from /data/routes.camel.yaml
DEBUG WanakuRoutesLoader - Found 3 routes in YAML
DEBUG WanakuRoutesLoader - Loading route 'get-employee-info'
DEBUG WanakuRoutesLoader - Route 'get-employee-info' started successfully
```

**Dependency resolution**:

```
DEBUG DependencyDownloader - Downloading org.apache.camel:camel-http:4.18.1
DEBUG DependencyDownloader - Resolved 12 transitive dependencies
DEBUG DependencyDownloader - Downloaded camel-http-4.18.1.jar to /tmp/camel-deps/
```

**Tool invocations**:

```
DEBUG CamelTool - Received invocation for tool 'get-employee-info'
DEBUG CamelTool - Mapping parameter 'employeeId' to header 'Wanaku.employeeId'
DEBUG CamelTool - Executing route 'get-employee-info'
DEBUG CamelTool - Route completed in 234ms
```

**Registration**:

```
DEBUG ZeroDepRegistrationManager - Attempting registration (attempt 1/12)
DEBUG ZeroDepRegistrationManager - Acquired OAuth2 token
DEBUG ZeroDepRegistrationManager - Registration successful, service ID: camel-12345
```

This level of detail makes it obvious where failures occur.

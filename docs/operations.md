# Operations

This guide covers production deployment, monitoring, and operational best practices for the Camel Integration Capability.

## Health Checks

The capability exposes a gRPC health probe for liveness and readiness checks.

### gRPC Health Probe

- **Protocol:** gRPC Health Checking Protocol
- **Port:** Same as gRPC server (default: 9190)
- **Service:** `grpc.health.v1.Health`
- **Availability:** The gRPC server starts **before** registration to avoid health check race conditions

### Kubernetes Health Checks

Configure liveness and readiness probes using the native Kubernetes gRPC probe support (requires Kubernetes 1.24+):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-integration-capability
spec:
  template:
    spec:
      containers:
      - name: camel-capability
        image: camel-integration-capability:0.1.1
        ports:
          - containerPort: 9190
            name: grpc
        livenessProbe:
          grpc:
            port: 9190
          initialDelaySeconds: 15
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          grpc:
            port: 9190
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2
```

**Probe Configuration Guidelines:**

- **Liveness Probe:**
  - `initialDelaySeconds: 15` — Allow time for Camel context initialization
  - `periodSeconds: 10` — Check every 10 seconds
  - `failureThreshold: 3` — Restart after 3 consecutive failures (30 seconds)

- **Readiness Probe:**
  - `initialDelaySeconds: 10` — Routes should be loaded by this point
  - `periodSeconds: 5` — Check frequently for traffic routing decisions
  - `failureThreshold: 2` — Remove from service after 2 failures (10 seconds)

### For Older Kubernetes Versions

If your cluster does not support native gRPC probes, use `grpc_health_probe` as an exec command:

```yaml
livenessProbe:
  exec:
    command:
      - /bin/grpc_health_probe
      - -addr=:9190
  initialDelaySeconds: 15
  periodSeconds: 10
readinessProbe:
  exec:
    command:
      - /bin/grpc_health_probe
      - -addr=:9190
  initialDelaySeconds: 10
  periodSeconds: 5
```

You'll need to include the `grpc_health_probe` binary in your container image. See the [gRPC Health Probe documentation](https://github.com/grpc-ecosystem/grpc-health-probe) for installation instructions.

## Resource Sizing

Resource requirements depend on route complexity and expected load. The table below provides baseline recommendations.

| Resource | Minimum | Recommended | Notes |
|----------|---------|-------------|-------|
| **CPU** | 0.5 cores | 1-2 cores | Depends on route complexity and invocation frequency |
| **Memory** | 512 MB | 1-2 GB | Includes Camel context, routes, and runtime dependencies |
| **Disk** | 100 MB | 1 GB | For dependency cache at `/data` (mount as persistent volume) |
| **Network** | - | Low latency to backend systems | Critical for route performance |

### Kubernetes Resource Limits

Define resource requests and limits to ensure stable operation:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-integration-capability
spec:
  template:
    spec:
      containers:
      - name: camel-capability
        image: camel-integration-capability:0.1.1
        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "2000m"
            memory: "2Gi"
        volumeMounts:
          - name: data
            mountPath: /data
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: camel-capability-data
```

**Resource Tuning:**

- **CPU**: Increase if routes perform heavy computation (JSON parsing, transformations, encryption)
- **Memory**: Increase if loading many routes or large dependencies (e.g., database drivers, XML processors)
- **Disk**: Increase if using many Camel components (each component may pull additional JARs)

## Startup Performance

Understanding startup phases helps tune health check timings and troubleshoot slow starts.

### Cold Start (First Run)

**Duration:** 30-60 seconds

**Phases:**

1. JVM initialization (2-5 seconds)
2. Dependency download from Maven Central (20-40 seconds)
3. Camel context creation (3-5 seconds)
4. Route loading and validation (2-5 seconds)
5. gRPC server startup (1-2 seconds)
6. Service registration with Wanaku Router (1-3 seconds)

**Optimization:**

- Pre-populate `/data` volume with downloaded dependencies
- Use a Maven mirror or repository manager (Nexus, Artifactory) closer to your deployment
- Build a custom container image with dependencies pre-installed (trade-off: larger image size)

### Warm Start (Dependencies Cached)

**Duration:** 10-20 seconds

**Phases:**

1. JVM initialization (2-5 seconds)
2. Dependency validation (1-2 seconds)
3. Camel context creation (3-5 seconds)
4. Route loading and validation (2-5 seconds)
5. gRPC server startup (1-2 seconds)
6. Service registration (1-3 seconds)

**When this happens:**

- Dependencies are already present in `/data`
- Pod restart or rolling update with persistent volume
- Horizontal scale-out with shared storage

### Runtime Performance

**Route Execution Overhead:**

- Apache Camel ProducerTemplate: sub-millisecond overhead
- Actual route performance depends on backend system latency

**gRPC Invocation Overhead:**

- Serialization/deserialization: 1-3ms
- Network latency (same cluster): 1-2ms
- Total gRPC overhead: 1-5ms per invocation

**Authentication Overhead:**

- Token is cached after first retrieval
- Per-request overhead: negligible (token included in headers)
- Token refresh: occurs in background before expiration

## Logging Configuration

The capability uses **SLF4J** with **Log4j2** as the logging backend.

### Configuration Files

Log4j2 can be configured using either format:

- `log4j2.properties` (simple key-value format)
- `log4j2.xml` (full XML configuration)

Place the configuration file in the classpath or specify its location:

```bash
java -Dlog4j2.configurationFile=/path/to/log4j2.xml \
  -jar camel-integration-capability-main.jar
```

### Key Loggers

| Logger Name | Purpose | Default Level |
|-------------|---------|---------------|
| `ai.wanaku.capability.camel.CamelToolMain` | Application lifecycle events (startup, shutdown, initialization) | INFO |
| `ai.wanaku.capability.camel.grpc.CamelTool` | Tool invocations, route execution, gRPC requests | INFO |
| `ai.wanaku.capability.camel.util.WanakuRoutesLoader` | Route loading, dependency resolution, YAML parsing | INFO |
| `ai.wanaku.capability.camel.util.McpRulesManager` | Rule processing, route exposure decisions | INFO |
| `org.apache.camel` | Apache Camel framework (route execution, component lifecycle) | INFO |
| `io.grpc` | gRPC server and client operations | WARN |

### Setting Log Levels

**Via log4j2.properties:**

```properties
# Root logger
rootLogger.level = info

# Application loggers
logger.camel-tool.name = ai.wanaku.capability.camel
logger.camel-tool.level = debug

logger.routes-loader.name = ai.wanaku.capability.camel.util.WanakuRoutesLoader
logger.routes-loader.level = debug

# Apache Camel
logger.camel.name = org.apache.camel
logger.camel.level = info

# gRPC (reduce noise)
logger.grpc.name = io.grpc
logger.grpc.level = warn
```

**Via environment variable (overrides config file):**

```bash
export LOG_LEVEL=DEBUG
java -jar camel-integration-capability-main.jar
```

**Via system property:**

```bash
java -Dlog4j2.rootLogger.level=DEBUG \
  -jar camel-integration-capability-main.jar
```

### Structured Logging

For production deployments, use JSON-formatted logs for easier parsing and analysis:

**log4j2.xml example:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <JsonLayout compact="true" eventEol="true" includeStacktrace="true">
                <KeyValuePair key="service" value="camel-integration-capability"/>
            </JsonLayout>
        </Console>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="Console"/>
        </Root>
        <Logger name="ai.wanaku.capability.camel" level="debug"/>
        <Logger name="org.apache.camel" level="info"/>
        <Logger name="io.grpc" level="warn"/>
    </Loggers>
</Configuration>
```

**Kubernetes ConfigMap for Log4j2:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: camel-capability-logging
data:
  log4j2.xml: |
    <?xml version="1.0" encoding="UTF-8"?>
    <Configuration status="WARN">
        <Appenders>
            <Console name="Console" target="SYSTEM_OUT">
                <JsonLayout compact="true" eventEol="true"/>
            </Console>
        </Appenders>
        <Loggers>
            <Root level="info">
                <AppenderRef ref="Console"/>
            </Root>
        </Loggers>
    </Configuration>
```

Mount as a volume:

```yaml
volumeMounts:
  - name: logging-config
    mountPath: /config/log4j2.xml
    subPath: log4j2.xml
volumes:
  - name: logging-config
    configMap:
      name: camel-capability-logging
```

## Registration Retry Tuning

The capability retries registration with the Wanaku MCP Router until successful. Tune these parameters based on your environment's startup characteristics.

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--initial-delay` | 5s | Time to wait before the first registration attempt |
| `--period` | 5s | Interval between registration attempts (when registered) |
| `--retries` | 12 | Maximum number of retry attempts on failure |
| `--wait-seconds` | 5s | Wait time between retries after a failure |

### Tuning Recommendations

**Slow-Starting Environments:**

If the Wanaku MCP Router takes time to start (e.g., during cluster initialization), increase the initial delay:

```bash
--initial-delay 30 \
--retries 20 \
--wait-seconds 10
```

This gives the router 30 seconds before the first attempt, then retries up to 20 times with 10-second waits (total: 30 + (20 × 10) = 230 seconds).

**Unreliable Networks:**

For environments with intermittent network issues, increase retries and wait time:

```bash
--retries 30 \
--wait-seconds 10
```

**Fast Environments:**

For local development or stable clusters where services start quickly:

```bash
--initial-delay 2 \
--period 3 \
--retries 5 \
--wait-seconds 3
```

### Registration Failure Scenarios

If registration fails after all retries, the service will:

1. Log an error message with the failure reason
2. Exit with a non-zero status code
3. Kubernetes will restart the pod (if using a Deployment)

**Common Failure Reasons:**

- Wanaku MCP Router is not accessible (check networking)
- Authentication failed (check credentials)
- Invalid registration request (check configuration)

## Scaling

The capability is **stateless** and designed for horizontal scaling.

### Horizontal Scaling

**Characteristics:**

- No shared state between instances
- Each instance registers independently with the Wanaku MCP Router
- Load balancing is handled by the Wanaku MCP Router
- Instances can be added or removed dynamically

**Kubernetes Deployment:**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-integration-capability
spec:
  replicas: 3  # Scale to 3 instances
  selector:
    matchLabels:
      app: camel-capability
  template:
    spec:
      containers:
      - name: camel-capability
        image: camel-integration-capability:0.1.1
        # ... configuration ...
```

**Horizontal Pod Autoscaler (HPA):**

Scale based on CPU or memory usage:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: camel-capability-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: camel-integration-capability
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Vertical Scaling

If routes are CPU or memory intensive, consider vertical scaling (increase resources per pod) instead of horizontal scaling.

**When to use:**

- Routes perform heavy computation
- Large dependency graphs
- Memory-intensive transformations

**How to scale:**

1. Increase CPU and memory limits in the Deployment
2. Monitor actual usage with metrics
3. Adjust based on observed patterns

### Load Distribution

The Wanaku MCP Router distributes tool invocations across registered capability instances. No configuration is needed on the capability side.

**Load Balancing Strategy:**

- Determined by the Wanaku MCP Router
- Typically round-robin or least-connections
- Check Wanaku documentation for details

## Container Best Practices

### Base Image

The provided `Dockerfile` uses **Red Hat Universal Base Image 9 (UBI9)** with **OpenJDK 21**:

```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-21:1.21
```

**Why UBI9?**

- Regularly updated with security patches
- Minimal attack surface
- Supported by Red Hat
- Compatible with OpenShift

### Data Directory

The capability uses `/data` as the default directory for:

- Downloaded Maven dependencies
- Temporary files
- Route cache

**Volume Mount:**

Mount a persistent volume to cache dependencies across restarts:

```yaml
volumeMounts:
  - name: data
    mountPath: /data
volumes:
  - name: data
    persistentVolumeClaim:
      claimName: camel-capability-data
```

**PersistentVolumeClaim:**

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: camel-capability-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

### Ports

Expose the gRPC port:

```dockerfile
EXPOSE 9190
```

```yaml
ports:
  - containerPort: 9190
    name: grpc
    protocol: TCP
```

### Resource Limits

Always set resource limits in production:

```yaml
resources:
  requests:
    cpu: "500m"
    memory: "512Mi"
  limits:
    cpu: "2000m"
    memory: "2Gi"
```

### Secrets Management

**Never** embed secrets in container images or environment variable values in manifests. Use Kubernetes Secrets:

```yaml
env:
  - name: CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: wanaku-credentials
        key: client-secret
```

**Best practices:**

- Create secrets via `kubectl create secret` (not in YAML)
- Rotate credentials regularly
- Use external secret managers (Vault, AWS Secrets Manager, etc.) for enterprise deployments
- Restrict RBAC access to secrets

### Container Security

**Run as non-root user:**

The UBI9 OpenJDK image already runs as a non-root user. Verify in your Dockerfile:

```dockerfile
USER 185
```

**Read-only root filesystem:**

```yaml
securityContext:
  readOnlyRootFilesystem: true
  runAsNonRoot: true
  allowPrivilegeEscalation: false
```

If using a read-only root filesystem, mount `/data` as a writable volume.

**Drop capabilities:**

```yaml
securityContext:
  capabilities:
    drop:
      - ALL
```

## Monitoring

Currently, the capability does not expose Prometheus metrics or OpenTelemetry traces. Monitoring relies on structured logging and Kubernetes-level observability.

### Current Observability

**Logs:**

- Structured JSON logs (via Log4j2)
- Kubernetes log aggregation (Fluentd, Fluent Bit, etc.)
- Centralized logging (Elasticsearch, Loki, CloudWatch)

**Kubernetes Metrics:**

- CPU and memory usage (via metrics-server)
- Pod restarts and health check failures
- Network traffic (service mesh metrics if using Istio, Linkerd, etc.)

### Future Considerations

Planned enhancements:

**Prometheus Metrics:**

- Route execution times
- Error rates per route
- gRPC request/response times
- Dependency download times
- Registration success/failure counts

**OpenTelemetry Tracing:**

- Distributed tracing across Wanaku services
- Per-route execution spans
- Correlation with backend system traces
- Integration with Jaeger, Zipkin, or cloud tracing services

**Health Metrics:**

- Camel context status
- Route health (up/down)
- Dependency resolution failures

Until these are implemented, use the following approaches:

**Application Performance Monitoring (APM):**

- Attach APM agents (Datadog, New Relic, Elastic APM) to the JVM
- Configure via `-javaagent` flag
- Monitor JVM metrics, garbage collection, and thread pools

**Custom Metrics via Routes:**

- Use Camel's built-in metrics components (`camel-micrometer`, `camel-metrics`)
- Export metrics from within routes
- Aggregate in Prometheus or a time-series database

## Troubleshooting

### Service Won't Start

**Check logs:**

```bash
kubectl logs -f deployment/camel-integration-capability
```

**Common causes:**

1. Missing required parameters (e.g., `--registration-url`)
2. Authentication failure (invalid credentials)
3. Cannot download routes or dependencies
4. Port 9190 already in use
5. Insufficient memory or disk space

**Solutions:**

- Verify all required CLI arguments are provided
- Check authentication configuration (see [Authentication](authentication.md))
- Verify network connectivity to DataStore and Maven Central
- Increase resource limits if OOMKilled

### Registration Failures

**Symptoms:**

- Service starts but never registers with Wanaku Router
- Logs show repeated registration attempts

**Check:**

1. Wanaku Router is running and accessible
2. `--registration-url` is correct
3. Authentication is configured correctly
4. Network policies allow egress to the router

**Increase retry parameters:**

```bash
--initial-delay 30 \
--retries 30 \
--wait-seconds 10
```

### Route Execution Errors

**Symptoms:**

- Tool invocations return errors
- Routes fail to execute

**Check:**

1. Route YAML syntax is valid
2. Required Camel components are listed in dependencies
3. Backend systems are accessible
4. Credentials for backend systems are correct

**Enable debug logging:**

```bash
export LOG_LEVEL=DEBUG
```

### High Memory Usage

**Symptoms:**

- Pods restarted due to OOMKilled
- Memory usage grows over time

**Causes:**

1. Too many routes loaded
2. Large dependencies (e.g., heavy XML processing libraries)
3. Memory leak in custom routes

**Solutions:**

- Increase memory limits
- Reduce number of routes per instance
- Review route logic for memory leaks
- Use JVM memory profiling tools (VisualVM, JProfiler)

### Slow Startup

**Symptoms:**

- Health checks fail before service is ready
- Startup takes longer than expected

**Causes:**

1. Slow dependency download (network latency to Maven Central)
2. Many dependencies to download
3. Large route files

**Solutions:**

- Use a Maven mirror closer to your deployment
- Pre-populate `/data` volume with dependencies
- Increase `initialDelaySeconds` in health checks
- Build a custom image with dependencies pre-installed

## Backup and Recovery

### What to Back Up

**Configuration:**

- Route YAML files
- Rule YAML files
- Dependency declarations
- Kubernetes manifests
- Log4j2 configuration

**Secrets:**

- OAuth2 client credentials
- Backend system credentials
- Certificates (if using mTLS)

**Data Volume:**

- Dependency cache in `/data` (optional — can be re-downloaded)

### Disaster Recovery

**Recovery Steps:**

1. **Restore Configuration**: Apply Kubernetes manifests from version control
2. **Restore Secrets**: Recreate secrets (from backup or secret manager)
3. **Deploy Service**: Kubernetes will pull the image and start pods
4. **Dependencies**: Will be re-downloaded on first startup (or restore from backup)
5. **Verification**: Check logs and health probes

**Recovery Time Objective (RTO):**

- Configuration restore: < 5 minutes
- Service startup (cold): 30-60 seconds
- Total RTO: < 10 minutes (assuming automation)

### High Availability

For mission-critical deployments:

- Run at least 2 replicas (`replicas: 2`)
- Use pod anti-affinity to spread across nodes
- Deploy across multiple availability zones
- Use PodDisruptionBudget to prevent simultaneous evictions

**Pod Anti-Affinity Example:**

```yaml
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchLabels:
              app: camel-capability
          topologyKey: kubernetes.io/hostname
```

**PodDisruptionBudget:**

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: camel-capability-pdb
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: camel-capability
```

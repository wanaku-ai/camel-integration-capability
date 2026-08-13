# Operations

This guide covers production deployment, monitoring, and operational best practices for the Camel Integration Capability.

## Health Checks

The capability exposes an HTTP health endpoint via the built-in MCP server.

### MCP Server Health

- **Protocol:** HTTP
- **Port:** Same as MCP server (default: 8080)

### Kubernetes Health Checks

Configure liveness and readiness probes using HTTP probes:

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
        image: camel-integration-capability:latest
        ports:
          - containerPort: 8080
            name: mcp
        livenessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 15
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2
```

**Probe Configuration Guidelines:**

- **Liveness Probe:** `initialDelaySeconds: 15` allows time for Camel context initialization
- **Readiness Probe:** `initialDelaySeconds: 10` allows time for route loading

## Resource Sizing

Resource requirements depend on route complexity and expected load.

| Resource | Minimum | Recommended | Notes |
|----------|---------|-------------|-------|
| **CPU** | 0.5 cores | 1-2 cores | Depends on route complexity and invocation frequency |
| **Memory** | 512 MB | 1-2 GB | Includes Camel context, routes, and runtime dependencies |
| **Disk** | 100 MB | 1 GB | For dependency cache at `/data` (mount as persistent volume) |

### Kubernetes Resource Limits

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
        image: camel-integration-capability:latest
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

## Startup Performance

### Cold Start (First Run)

**Duration:** 30-60 seconds

**Phases:**

1. JVM initialization (2-5 seconds)
2. Dependency download from Maven Central (20-40 seconds)
3. Camel context creation (3-5 seconds)
4. Route loading and validation (2-5 seconds)
5. MCP server startup (1-2 seconds)

**Optimization:**

- Pre-populate `/data` volume with downloaded dependencies
- Use a Maven mirror or repository manager closer to your deployment
- Build a custom container image with dependencies pre-installed

### Warm Start (Dependencies Cached)

**Duration:** 10-20 seconds

## Logging Configuration

The capability uses **SLF4J** with **Log4j2** as the logging backend.

### Key Loggers

| Logger Name | Purpose | Default Level |
|-------------|---------|---------------|
| `ai.wanaku.capability.camel.CamelToolMain` | Application lifecycle events | INFO |
| `ai.wanaku.capability.camel.WanakuCamelManager` | Route loading, MCP server configuration | INFO |
| `org.apache.camel` | Apache Camel framework events | INFO |

### Setting Log Levels

**Via environment variable:**

```bash
export LOG_LEVEL=DEBUG
java -jar camel-integration-capability-main.jar
```

### Structured Logging

For production deployments, use JSON-formatted logs for easier parsing:

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
    </Loggers>
</Configuration>
```

## Scaling

The capability is **stateless** and designed for horizontal scaling.

### Horizontal Scaling

- No shared state between instances
- Instances can be added or removed dynamically
- Load balancing handled by Kubernetes Services or Wanaku MCP Router

**Kubernetes Deployment:**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-integration-capability
spec:
  replicas: 3
  selector:
    matchLabels:
      app: camel-capability
  template:
    spec:
      containers:
      - name: camel-capability
        image: camel-integration-capability:latest
```

**Horizontal Pod Autoscaler:**

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
```

## Container Best Practices

### Base Image

The provided `Dockerfile` uses **Red Hat Universal Base Image 9 (UBI9)** with **OpenJDK 21**.

### Data Directory

The capability uses `/data` as the default directory for downloaded Maven dependencies and temporary files. Mount a persistent volume to cache dependencies across restarts.

### Ports

Expose the MCP server port:

```yaml
ports:
  - containerPort: 8080
    name: mcp
    protocol: TCP
```

### Container Security

**Run as non-root user:**

The UBI9 OpenJDK image already runs as a non-root user.

**Read-only root filesystem:**

```yaml
securityContext:
  readOnlyRootFilesystem: true
  runAsNonRoot: true
  allowPrivilegeEscalation: false
```

If using a read-only root filesystem, mount `/data` as a writable volume.

## Monitoring

Currently, the capability does not expose Prometheus metrics or OpenTelemetry traces. Monitoring relies on structured logging and Kubernetes-level observability.

### Current Observability

- Structured JSON logs (via Log4j2)
- Kubernetes metrics (CPU, memory via metrics-server)
- Pod restarts and health check failures

### Future Considerations

- Prometheus metrics export (route execution times, error rates)
- OpenTelemetry integration (distributed tracing)

## Backup and Recovery

### What to Back Up

- Route YAML files
- Dependency declarations
- Kubernetes manifests
- Log4j2 configuration

### High Availability

For mission-critical deployments:

- Run at least 2 replicas
- Use pod anti-affinity to spread across nodes
- Deploy across multiple availability zones
- Use PodDisruptionBudget to prevent simultaneous evictions

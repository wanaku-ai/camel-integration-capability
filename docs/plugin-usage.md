# Camel Integration Capability Plugin

The Camel Integration Capability can be embedded into an existing Apache Camel application as a plugin using the standard `ContextServicePlugin` SPI mechanism.

## Installation

Add the plugin dependency to your project:

```xml
<dependency>
    <groupId>ai.wanaku</groupId>
    <artifactId>camel-integration-capability-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The plugin is automatically discovered via Java's ServiceLoader when the jar is on the classpath. No additional configuration is needed in your Camel application code.

## Configuration

The plugin is configured through a properties file and/or environment variables. Environment variables take precedence over properties file values.

### Properties File

Create a file named `camel-integration-capability.properties` in your classpath (e.g., `src/main/resources/`):

```properties
# Registration (required)
registration.url=http://localhost:8080

# Registration announce address (default: auto)
registration.announce.address=auto

# gRPC Configuration (default: 9190)
grpc.port=9190

# Service Identity (default: camel)
service.name=my-service

# Routes Configuration (required)
routes.ref=file:///path/to/routes.yaml

# Rules (optional)
rules.ref=file:///path/to/rules.yaml

# Authentication (required)
token.endpoint=http://localhost:8080/oauth/token
client.id=my-client
client.secret=my-secret

# Dependencies (optional)
dependencies=

# Repositories (optional)
repositories=

# Initialization (optional)
init.from=git@github.com:example/repo.git
data.dir=/tmp
```

### Environment Variables

All properties can be overridden using environment variables. This is the recommended approach for container deployments (OpenShift, Kubernetes).

| Property                        | Environment Variable            | Description                            |
|---------------------------------|---------------------------------|----------------------------------------|
| `registration.url`              | `REGISTRATION_URL`              | Wanaku registration endpoint URL       |
| `registration.announce.address` | `REGISTRATION_ANNOUNCE_ADDRESS` | Address announced to discovery service |
| `grpc.port`                     | `GRPC_PORT`                     | gRPC server port                       |
| `service.name`                  | `SERVICE_NAME`                  | Service name for registration          |
| `routes.ref`                    | `ROUTES_PATH`                   | Reference to Camel routes YAML         |
| `rules.ref`                     | `ROUTES_RULES`                  | Reference to exposure rules YAML       |
| `token.endpoint`                | `TOKEN_ENDPOINT`                | OAuth token endpoint                   |
| `client.id`                     | `CLIENT_ID`                     | OAuth client ID                        |
| `client.secret`                 | `CLIENT_SECRET`                 | OAuth client secret                    |
| `dependencies`                  | `DEPENDENCIES`                  | Comma-separated dependency refs        |
| `repositories`                  | `REPOSITORIES`                  | Comma-separated repository URLs        |
| `init.from`                     | `INIT_FROM`                     | Git repository URL to clone            |
| `data.dir`                      | `DATA_DIR`                      | Data directory path                    |

## OpenShift / Kubernetes Deployment

When deploying to OpenShift or Kubernetes, configure the plugin using environment variables in your deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-camel-app
spec:
  template:
    spec:
      containers:
        - name: app
          image: my-camel-app:latest
          env:
            - name: REGISTRATION_URL
              value: "http://wanaku-router:8080"
            - name: SERVICE_NAME
              value: "my-camel-service"
            - name: GRPC_PORT
              value: "9190"
            - name: ROUTES_PATH
              value: "file:///data/routes.yaml"
            - name: CLIENT_ID
              valueFrom:
                secretKeyRef:
                  name: wanaku-credentials
                  key: client-id
            - name: CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: wanaku-credentials
                  key: client-secret
          ports:
            - containerPort: 9190
              name: grpc
```

## Plugin Lifecycle

The plugin integrates with Apache Camel's lifecycle:

1. **load()** - Called after CamelContext creation, before routes start:
   - Loads configuration from properties/environment
   - Initializes data directory
   - Runs initializers (e.g., git clone)
   - Downloads resources
   - Starts gRPC server
   - Registers with discovery service

2. **unload()** - Called during CamelContext shutdown:
   - Deregisters from discovery service
   - Stops gRPC server
   - Releases resources

## Comparison with Standalone Application

| Feature           | Plugin                               | Standalone                   |
|-------------------|--------------------------------------|------------------------------|
| Configuration     | Properties + Environment             | CLI arguments                |
| Deployment        | Embedded in existing app             | Separate process             |
| Container support | Via host app                         | Native Dockerfile            |
| Use case          | Add capability to existing Camel app | Dedicated capability service |

For standalone deployment, see [usage.md](usage.md).

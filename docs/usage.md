# Camel Core Downstream Service

A capability service for the [Wanaku MCP Router](https://wanaku.ai) that provides [Apache Camel](https://camel.apache.org) route
execution capabilities.

This service integrates with the Wanaku ecosystem to execute Camel routes dynamically through Wanaku's gRPC bridge. 

## Overview

This service implements a Wanaku capability that can:
- Execute Apache Camel routes defined in YAML format
- Register itself with a Wanaku discovery service
- Support service-to-router authentication via OAuth2/OIDC

> [TIP]
> Design your Camel routes with ease using the [Kaoto Integration Designer](http://kaoto.io) for Apache Camel.

## Requirements

- Java 21 or higher
- Maven 3.6+ for building
- Access to a Wanaku discovery service
- OAuth2/OIDC authentication provider

## Running this Capability

This capability service requires configuration parameters to connect to the Wanaku ecosystem. When launching it, you need to 
set them so that this capability service can talk to Wanaku and register itself. 

> [NOTE]
> Although this capability is intended to be run inside Kubernetes or OpenShift, it is entirely possible to execute it locally.

### Required Parameters

- `--registration-url`: URL of the Wanaku discovery service
- `--registration-announce-address`: Address to announce for service discovery
- `--routes-ref`: Reference to the Apache Camel routes YAML file. Supports `datastore://` and `file://` schemes
- `--token-endpoint`: OAuth2/OIDC token endpoint base URL
- `--client-id`: OAuth2 client ID for authentication
- `--client-secret`: OAuth2 client secret for authentication

### Optional Parameters

- `--rules-ref`: Reference to the YAML file with route exposure rules. Supports `datastore://` and `file://` schemes
- `--dependencies`: Comma-separated list of dependencies. Supports `datastore://` and `file://` schemes
- `--init-from`: Git repository URL to clone during initialization (SSH or HTTPS format)
- `--grpc-port`: gRPC server port (default: 9190)
- `--name`: Service name for registration (default: "camel")
- `--retries`: Maximum registration retries (default: 12)
- `--wait-seconds`: Wait time between retries in seconds (default: 5)
- `--initial-delay`: Initial registration delay in seconds (default: 5)
- `--period`: Period between registration attempts in seconds (default: 5)
- `--data-dir`: Directory where downloaded files will be saved (default: `/tmp` for CLI, `/data` for Docker)

### URI Schemes

The service supports multiple URI schemes for resource references:

- **datastore://**: Fetches files from the Wanaku DataStore service.
- **file://**: References local files (absolute paths required)

### Basic Example (Local)

For local development with a Wanaku stack:

```bash
java -jar target/camel-integration-capability-1.0-SNAPSHOT.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --grpc-port 9190 \
  --name camel-core \
  --routes-ref datastore://promote-employee.camel.yaml \
  --rules-ref datastore://promote-employee-rules.yaml \
  --token-endpoint http://localhost:8543/realms/wanaku/ \
  --client-id wanaku-service \
  --client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv \
  --dependencies datastore://promote-employee-dependencies.txt \
  --data-dir /tmp/camel-data
```

Where `promote-employee-dependencies.txt` is a text file containing the dependencies in a comma-separated list:

```shell
org.apache.camel:camel-http:4.14.2,org.apache.camel:camel-jackson:4.14.2
```

## Deploying the Service

The service can be deployed to Kubernetes or OpenShift using the Wanaku's operator. 

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: Wanaku
metadata:
  name: wanaku-dev
spec:
  auth:
    authServer: http://address-of-the-keycloak-instance
    # Address of the proxy (could be the same as the auth server - default) or "auto" (for using Wanaku as the proxy via OIDC proxy)
    authProxy: "auto"
  router:
    image: quay.io/wanaku/wanaku-router-backend:latest
    imagePullPolicy: Always
  secrets:
    oidcCredentialsSecret: <credentials-go-here>
  capabilities:
    - name: wanaku-http
      image: quay.io/wanaku/wanaku-tool-service-http:latest
    - name: employee-system
      type: camel-integration-capability
      image: quay.io/wanaku/camel-integration-capability:latest
      # When using a camel integration capability, must always set the routes and rules references
      env:
        # Reference to the Camel routes YAML file (supports datastore:// and file:// schemes)
        - name: ROUTES_REF
          value: "datastore://employee-backend.camel.yaml"
        # Reference to the route exposure rules YAML (supports datastore:// and file:// schemes)
        - name: RULES_REF
          value: "datastore://employee-backend-rules.yaml"
        # Reference to dependencies file (supports datastore:// and file:// schemes)
        - name: DEPENDENCIES
          value: "datastore://employee-backend-dependencies.txt"
```

#### Required Configuration

| Parameter    | Description                                                | Example                        |
|--------------|------------------------------------------------------------|--------------------------------|
| `ROUTES_REF` | Reference to the Camel routes YAML file                    | `datastore://route.camel.yaml` |
| `RULES_REF`  | Reference to the route exposure rules YAML                 | `datastore://route-rules.yaml` |
| `DEPENDENCIES` | Reference to a text file containing a list of dependencies | `datastore://dependencies.txt` |

> [NOTE]
> See the running documentation below for details on each of these.

#### Optional Configuration

| Parameter   | Description                                 | Example                                      |
|-------------|---------------------------------------------|----------------------------------------------|
| `INIT_FROM` | Git repository URL to clone during startup  | `git@github.com:org/repo.git`                |
| `DATA_DIR`  | Directory where downloaded files are saved  | `/data`                                      |

> [NOTE]
> See the running documentation below for details on each of these.

### Troubleshooting

Common issues and solutions:

**Routes not found:**
```bash
# Verify git clone succeeded
kubectl exec deployment/camel-integration-capability -- ls -la /data
```

**Service not registering:**
```bash
# Check environment variables
kubectl exec deployment/camel-integration-capability -- env | grep -E "(REGISTRATION|TOKEN)"

# View application logs
kubectl logs -f deployment/camel-integration-capability -c camel-integration-capability
```

**Connection refused errors:**
```bash
# Verify service is running
kubectl get svc camel-integration-capability

# Check pod status
kubectl describe pod -l app=camel-integration-capability

# Test connectivity
kubectl run test-pod --rm -it --image=busybox -- telnet camel-integration-capability 9190
```

## Designing Routes

The easiest way to design the routes for this project, is to use a visual editor such as [Kaoto](http://kaoto.io) or 
[Camel Karavan](http://camel.apache.org/karavan) to design the routes. 

Those editors should allow you to visualize the route. For instance: 

![Kaoto Route Example](imgs/kaoto.png)

### Camel Routes

Camel routes should be defined in YAML format in the file specified by `--routes-ref`. Example route structure:

```yaml
- route:
    id: example-route
    from:
      uri: direct
      parameters:
        name: start
      steps:
        - log:
            message: Hello ${body}
        - setBody:
            simple: Hello Camel from ${routeId}
```

### Route Exposure Rules

Routes can be exposed as MCP tools or resources by defining rules in a YAML file specified by `--rules-ref`.
This file maps route definitions to tool specifications:

```yaml
mcp:
  tools:
    - initiate-employee-promotion:
        route:
          id: "route-3103"
        description: "Initiate the promotion process for an employee"
        properties:
          - name: employee
            type: string
            description: The employee to confirm the promotion
            required: true
            mapping:
              type: header
              name: EMPLOYEE
    - confirm-employee-promotion:
        route:
          id: "route-3104"
        description: "Confirm the promotion of an an employee"
        properties:
          - name: employee
            type: string
            description: The employee to confirm the promotion
            required: true
            mapping:
              type: header
              name: EMPLOYEE
  resources:
    - employee-performance-history:
        route:
          id: "route-3105"
        description: "Obtain the employee performance history"
```

For resource routes, the code doesn't necessarily run the route. Instead, it
uses only the endpoint URI for accessing it via a consumer template. For resources
the data MUST be convertable to a Java String.

> [IMPORTANT]
> Routes serving resources MUST have their auto-start disabled.


#### Property Mapping

Properties can include an optional `mapping` element to specify how parameters should be passed to the Camel route:

- `type`: The mapping type (e.g., `header`, `body`)
- `name`: The target name in the Camel exchange (e.g., header name)

### Handling Dependencies 

The capability only comes with a subset of the Apache Camel dependencies. 
In many cases, it will be necessary to provide a list of additional dependencies required for running it.
The capability can automatically download and include on the classpath all these dependencies.

For instance, to include the `camel-http` and `camel-jackson` dependencies, you can create a text file 
with the following contents:

```
org.apache.camel:camel-http:4.14.2,org.apache.camel:camel-jackson:4.14.2
```

Then publish it to Wanaku's Data Store (or, git, if using the git initializer) and refer to the file accordingly

* `--dependencies datastore://filename.txt` if using the data store 
* `--dependencies file:///path/to/filename.txt` if using the git initializer

## Running the Capability and Exposing Camel Routes

After designing the routes, you will need to have the capability use them and expose them as MCP 
tools or MCP resources. To do so, the capability needs to have access to the files. 

Route files can be provided to the capability using one of the following methods:

1. **From Wanaku's Data Store**: This uses Wanaku's Data Store to download files automatically after registration.
2. **Built-in Git initialization**: Use `--init-from` to clone a repository during startup
3. **Init container**: Use a separate container to clone files before the main container starts (see example below)
4. **Volume mounts**: Mount ConfigMaps or persistent volumes containing route files

### Using Wanaku's Data Store

This is the recommended way to obtain the route files. In this mode, the capability downloads files
directly from Wanaku after its registration is complete.

When running manually, the command looks like this:

```bash
java -jar target/camel-integration-capability-1.0-SNAPSHOT.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --grpc-port 9190 \
  --name camel-core \
  --routes-ref datastore://promote-employee.camel.yaml \
  --rules-ref datastore://promote-employee-rules.yaml \
  --dependencies datastore://promote-employee-dependencies.txt \
  --token-endpoint http://localhost:8543/realms/wanaku/ \
  --client-id wanaku-service \
  --client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv \
  --data-dir /tmp
```

### Using Git Initialization

To clone a Git repository containing routes and reference files directly:

```bash
java -jar target/camel-integration-capability-1.0-SNAPSHOT.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --grpc-port 9190 \
  --name camel-core \
  --init-from git@github.com:wanaku-ai/wanaku-recipes.git \
  --routes-ref file:///tmp/cloned-repo/routes/promote-employee.camel.yaml \
  --rules-ref file:///tmp/cloned-repo/rules/promote-employee-rules.yaml \
  --dependencies file:///tmp/cloned-repo/dependencies/promote-employee-dependencies.txt \
  --token-endpoint http://localhost:8543/realms/wanaku/ \
  --client-id wanaku-service \
  --client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv \
  --data-dir /tmp
```

The `--init-from` option clones the repository to `/tmp/cloned-repo` during startup.
Files are then referenced using `file://` URIs with absolute paths.

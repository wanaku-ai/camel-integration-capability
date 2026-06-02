# Authentication

This document provides a deep dive into OAuth2/OIDC authentication for the Camel Integration Capability.

## Overview

The Camel Integration Capability uses OAuth2 Client Credentials flow for service-to-service authentication. Access tokens are required for:

- **Registration**: Authenticating with the Wanaku MCP Router during service registration
- **DataStore Access**: Fetching routes, rules, and dependencies from the DataStore service

Since version 0.1.0, authentication is **optional**. When the Wanaku MCP Router is configured without authentication, you can omit the `--client-secret` parameter while still providing a `--client-id` (used as the service identifier).

## Client Credentials Flow

The service follows this authentication sequence at startup:

1. **Initialization**: Service reads `--client-id` and `--client-secret` from CLI arguments or environment variables
2. **Token Endpoint Resolution**:
   - If `--token-endpoint` is provided, use it directly
   - Otherwise, auto-resolve from `--registration-url` (works with standard OAuth2 discovery endpoints)
3. **Token Request**: Service requests an access token using client credentials
4. **Token Usage**: Token is included as a Bearer token in all registration and DataStore HTTP requests
5. **Automatic Refresh**: Token is automatically refreshed before expiration

## Configuration Parameters

### Authentication Parameters

| Parameter | Environment Variable | Description |
|-----------|---------------------|-------------|
| `--client-id` | `CLIENT_ID` | OAuth2 client ID. Required when `--client-secret` is provided. |
| `--client-secret` | `CLIENT_SECRET` | OAuth2 client secret. Required only when the Wanaku MCP Router enforces authentication. Omit when authentication is disabled. |
| `--token-endpoint` | `TOKEN_ENDPOINT` | Base URL for the OAuth2 token endpoint. If not specified, auto-resolved from `--registration-url`. For Keycloak, must include the realm path. |

### Command-Line Examples

**With authentication (Keycloak):**

```bash
java -jar camel-integration-capability-main.jar \
  --client-id my-service \
  --client-secret super-secret \
  --token-endpoint http://keycloak:8543/realms/my-realm/ \
  --registration-url http://wanaku-router:8080
```

**Without authentication:**

```bash
java -jar camel-integration-capability-main.jar \
  --registration-url http://wanaku-router:8080
```

**Auto-resolve token endpoint:**

```bash
java -jar camel-integration-capability-main.jar \
  --client-id my-service \
  --client-secret super-secret \
  --registration-url http://wanaku-router:8080
```

## Keycloak Configuration

When using Keycloak as your OAuth2 provider, there are specific configuration requirements.

### Token Endpoint URL

The `--token-endpoint` parameter must include the full realm path. The service will automatically append the OIDC protocol path.

**Structure:**

```text
--token-endpoint http://<keycloak-host>:<port>/realms/<realm-name>/
```

**Examples:**

```bash
# Production realm
--token-endpoint http://keycloak:8543/realms/production/

# Development realm
--token-endpoint http://localhost:8080/realms/dev/

# Custom realm name
--token-endpoint https://auth.example.com/realms/my-organization/
```

The service resolves the complete token endpoint as:

```text
<base-url>/protocol/openid-connect/token
```

For example, `http://keycloak:8543/realms/my-realm/` becomes:

```text
http://keycloak:8543/realms/my-realm/protocol/openid-connect/token
```

### Realm Name

**There is no hardcoded default realm.** You must explicitly specify the realm name in the `--token-endpoint` URL. This design ensures:

- Full flexibility: Use any realm naming convention
- No hidden assumptions: Configuration is explicit and auditable
- Multi-tenancy support: Different deployments can use different realms

### Client Setup in Keycloak

To create an OAuth2 client for the Camel Integration Capability:

1. **Navigate to Clients** in your Keycloak realm
2. **Create a new client**:
   - **Client ID**: Match your `--client-id` parameter (e.g., `camel-integration-capability`)
   - **Client Protocol**: `openid-connect`
3. **Configure client settings**:
   - **Client authentication**: ON (required for client credentials flow)
   - **Standard flow**: Disabled
   - **Direct access grants**: Disabled
   - **Service accounts roles**: Enabled (this enables the client credentials flow)
4. **Assign roles**:
   - Navigate to the "Service Account Roles" tab
   - Assign appropriate realm or client roles for accessing Wanaku services
5. **Get credentials**:
   - Navigate to the "Credentials" tab
   - Copy the client secret
   - Use this as the `--client-secret` parameter

## Disabling Authentication

Since version 0.1.0, you can run the capability without OAuth2 authentication when the Wanaku MCP Router is configured to allow unauthenticated access.

**When to disable authentication:**

- Local development environments
- Proof-of-concept deployments
- Isolated networks where authentication is handled at the infrastructure level

**How to disable:**

- Omit both the `--client-secret` and `--client-id` parameters (or their environment variables)

**Example:**

```bash
java -jar camel-integration-capability-main.jar \
  --registration-url http://localhost:8080
```

**Security considerations:**

- Only disable authentication in trusted environments
- Never expose unauthenticated services to the public internet
- Use network-level security (firewalls, VPCs) when authentication is disabled

## Environment Variables

All CLI parameters can be provided via environment variables. This is the recommended approach for container deployments.

| CLI Parameter | Environment Variable | Example |
|--------------|---------------------|---------|
| `--client-id` | `CLIENT_ID` | `export CLIENT_ID=my-service` |
| `--client-secret` | `CLIENT_SECRET` | `export CLIENT_SECRET=super-secret` |
| `--token-endpoint` | `TOKEN_ENDPOINT` | `export TOKEN_ENDPOINT=http://keycloak:8543/realms/prod/` |

**Shell example:**

```bash
export CLIENT_ID=camel-integration-capability
export CLIENT_SECRET=abc123def456
export TOKEN_ENDPOINT=http://keycloak:8543/realms/production/

java -jar camel-integration-capability-main.jar \
  --registration-url http://wanaku-router:8080
```

## Kubernetes Secrets

For production Kubernetes deployments, store OAuth2 credentials in Kubernetes Secrets and inject them as environment variables.

### Creating the Secret

```bash
kubectl create secret generic wanaku-credentials \
  --from-literal=client-id=camel-integration-capability \
  --from-literal=client-secret=abc123def456 \
  --from-literal=token-endpoint=http://keycloak:8543/realms/production/
```

### Deployment Manifest

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-integration-capability
spec:
  replicas: 2
  selector:
    matchLabels:
      app: camel-capability
  template:
    metadata:
      labels:
        app: camel-capability
    spec:
      containers:
      - name: camel-capability
        image: camel-integration-capability:0.1.1
        env:
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
          - name: TOKEN_ENDPOINT
            valueFrom:
              secretKeyRef:
                name: wanaku-credentials
                key: token-endpoint
          - name: REGISTRATION_URL
            value: "http://wanaku-router:8080"
        ports:
          - containerPort: 9190
            name: grpc
```

**Security best practices:**

- Never commit secrets to version control
- Use RBAC to restrict access to the `wanaku-credentials` secret
- Rotate credentials regularly
- Consider using external secret management (HashiCorp Vault, AWS Secrets Manager, etc.)

## Troubleshooting Authentication

### Token Endpoint Not Found

**Symptom:** Service fails to start with an error about token endpoint resolution.

**Causes:**

1. `--token-endpoint` not provided and auto-resolution failed
2. `--registration-url` does not support OAuth2 discovery

**Solutions:**

- Explicitly set `--token-endpoint` with the full realm path (for Keycloak)
- Verify the registration URL is correct
- Check network connectivity to the authentication server

### 401 Unauthorized

**Symptom:** Service fails to register or fetch resources with HTTP 401 errors.

**Causes:**

1. Invalid client credentials
2. Client not properly configured in OAuth2 provider
3. Service account roles not assigned
4. Token expired (unlikely during startup)

**Solutions:**

- Verify `--client-id` matches the client ID in Keycloak/OAuth2 provider
- Verify `--client-secret` matches the credential in the Credentials tab
- Check that "Client authentication" is ON in Keycloak
- Verify "Service accounts roles" is enabled
- Check that appropriate roles are assigned to the service account
- Review Keycloak logs for authentication failures

### Connection Refused

**Symptom:** Service cannot connect to the token endpoint.

**Causes:**

1. Token endpoint URL is incorrect
2. Authentication server is not running
3. Network connectivity issues
4. Firewall blocking the connection

**Solutions:**

- Verify the token endpoint URL format (must include realm for Keycloak)
- Check that Keycloak/OAuth2 server is running: `curl http://keycloak:8543/realms/my-realm/`
- Test network connectivity from the capability container/pod
- Check Kubernetes NetworkPolicies or firewall rules

### Realm Not Found (Keycloak)

**Symptom:** Token endpoint returns 404 or "Realm does not exist" error.

**Causes:**

1. Realm name is misspelled in `--token-endpoint`
2. Realm does not exist in Keycloak

**Solutions:**

- Verify the realm name in Keycloak admin console
- Check the URL format: `http://keycloak:8543/realms/<realm-name>/`
- Realm names are case-sensitive
- Use the Keycloak admin API to list realms: `curl http://keycloak:8543/admin/realms`

### Token Endpoint Incorrect for Keycloak

**Symptom:** Service tries to connect to a malformed token endpoint URL.

**Cause:** The `--token-endpoint` parameter is missing the `/realms/<realm-name>/` path.

**Solution:**

```bash
# Wrong
--token-endpoint http://keycloak:8543

# Correct
--token-endpoint http://keycloak:8543/realms/production/
```

## Security Considerations

### Credential Storage

- **Never** embed credentials in container images
- **Never** commit credentials to version control
- **Always** use environment variables or secret management systems
- Rotate credentials regularly (quarterly at minimum)

### Network Security

- Use HTTPS for token endpoints in production
- Restrict network access to the token endpoint (firewall rules, NetworkPolicies)
- Consider mutual TLS (mTLS) for additional security

### Token Scoping

- Use the principle of least privilege: grant only necessary roles
- Create dedicated service accounts for each capability instance
- Avoid using admin or overly broad roles

### Logging

- OAuth2 client secrets are **never** logged by the capability
- Access tokens are logged at TRACE level only (disabled in production)
- Review logs for authentication failures to detect potential attacks

## Advanced Configuration

### Custom OAuth2 Providers

The capability supports any OAuth2 provider that implements the client credentials flow:

- **Okta**
- **Auth0**
- **Azure AD**
- **Google Cloud Identity**
- **Custom OAuth2 servers**

**Requirements:**

1. OAuth2 provider must support client credentials grant type
2. Token endpoint must return a standard access token response
3. Bearer token must be accepted by the Wanaku MCP Router

### Token Refresh

Token refresh is handled automatically by the Wanaku SDK:

- Tokens are refreshed before expiration
- Default refresh threshold: 5 minutes before expiry
- If refresh fails, the service logs an error and retries on the next operation

### Multiple Environments

For deployments across multiple environments (dev, staging, production), use separate Keycloak realms and clients:

**Development:**

```bash
--client-id camel-capability-dev
--client-secret dev-secret
--token-endpoint http://keycloak-dev:8080/realms/development/
```

**Production:**

```bash
--client-id camel-capability-prod
--client-secret prod-secret
--token-endpoint https://keycloak.example.com/realms/production/
```

This isolation ensures:

- Separate credentials per environment
- Different role assignments
- Independent audit logs
- No cross-environment credential leakage

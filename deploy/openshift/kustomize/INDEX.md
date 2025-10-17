# Kubernetes/OpenShift Deployment

This directory contains Kustomize manifests for deploying the Camel Core Downstream Service across multiple environments.

## Kustomize Deployment

For comprehensive documentation, see [KUSTOMIZE.md](KUSTOMIZE.md).

**Quick Start:**
```bash
# Deploy to dev environment
kubectl apply -k deploy/openshift/overlays/dev

# Deploy to prod environment
kubectl apply -k deploy/openshift/overlays/prod

# Preview what will be deployed
kustomize build deploy/openshift/overlays/dev
```

**Structure:**
- `base/` - Base manifests shared across all environments
- `overlays/dev/` - Development environment configuration
- `overlays/prod/` - Production environment configuration

## Files

### Base Manifests
- `base/deployment.yaml` - Main deployment with init container for git clone
- `base/configmap.yaml` - ConfigMap for configuration
- `base/secret.yaml` - Secret template for sensitive data
- `base/kustomization.yaml` - Base Kustomize configuration

### Environment Overlays
- `overlays/dev/kustomization.yaml` - Development environment overrides
- `overlays/prod/kustomization.yaml` - Production environment overrides

### Using ConfigMap (Optional)

To use the ConfigMap for configuration:

1. Edit `configmap.yaml` with your values
2. Update `deployment.yaml` to reference ConfigMap values using `valueFrom`

### Using Secrets (Optional)

For sensitive data like `CLIENT_SECRET`:

1. Add your base64-encoded secrets: `echo -n "your-secret" | base64`
2. Run:

```bash
# Example: Create secret manually
kubectl create secret generic camel-downstream-secrets \
  --from-literal=client-secret=YOUR_SECRET_HERE \
  --from-literal=token-endpoint=YOUR_TOKEN_ENDPOINT
```

## Init Container

The deployment includes an init container that:
- Uses UBI9 minimal image with git
- Clones the repository specified in `GIT_REPO_URL` environment variable
- Stores the cloned repository in `/data` directory
- Default repository: `http://github.com/orpiske/wanaku-cic-demo`

To use a different repository, modify the `GIT_REPO_URL` value in `deployment.yaml`:

```yaml
env:
- name: GIT_REPO_URL
  value: "https://github.com/your-org/your-repo"
```

## Environment Variables

The following environment variables can be configured in `deployment.yaml`:

| Variable | Description | Default |
|----------|-------------|---------|
| `GIT_REPO_URL` | Git repository URL to clone | `http://github.com/orpiske/wanaku-cic-demo` |
| `REGISTRATION_URL` | Service registration URL | "" |
| `REGISTRATION_ANNOUNCE_ADDRESS` | Address to announce | "" |
| `GRPC_PORT` | gRPC server port | `9190` |
| `SERVICE_NAME` | Service name | "" |
| `ROUTES_PATH` | Path to routes | `/data/wanaku-cic-demo` |
| `ROUTES_RULES` | Routes rules | "" |
| `TOKEN_ENDPOINT` | OAuth token endpoint | "" |
| `CLIENT_ID` | OAuth client ID | "" |
| `CLIENT_SECRET` | OAuth client secret | "" |

## OpenShift Specific

If deploying on OpenShift, you may need to:

1. Create an ImageStream:
```bash
oc create imagestream camel-core-downstream-service
```

2. Build and push the image:
```bash
podman build -t camel-core-downstream-service:latest .
podman tag camel-core-downstream-service:latest <registry>/camel-core-downstream-service:latest
podman push <registry>/camel-core-downstream-service:latest
```

3. Update the deployment to use the ImageStream or registry URL.

## Resource Limits

Default resource limits are set in `deployment.yaml`:
- Requests: 512Mi memory, 250m CPU
- Limits: 1Gi memory, 1000m CPU

Adjust these based on your workload requirements.

## Health Checks

The deployment includes:
- **Liveness probe**: TCP check on port 9190
- **Readiness probe**: TCP check on port 9190

These ensure the service is running and ready to accept connections.

## Troubleshooting

```bash
# Check init container logs
kubectl logs -f deployment/camel-core-downstream-service -c git-clone

# Check main container logs
kubectl logs -f deployment/camel-core-downstream-service -c camel-core-downstream-service

# Verify git clone succeeded
kubectl exec deployment/camel-core-downstream-service -- ls -la /data

# Check environment variables
kubectl exec deployment/camel-core-downstream-service -- env | grep -E "(GIT_REPO|ROUTES_PATH)"
```

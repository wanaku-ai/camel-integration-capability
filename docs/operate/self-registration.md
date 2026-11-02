---
title: Self-registration with Wanaku
description: Let the Camel Integration Capability add itself to Wanaku as an MCP forward when it starts, and remove the forward when it stops.
---

# Self-registration with Wanaku

By default, the capability does not contact Wanaku. You add it to Wanaku yourself, for example with `wanaku forwards add`.
With self-registration, the capability adds itself as an MCP forward when it starts and removes the forward when it stops.
Use it when the capability runs on a platform that starts and replaces instances for you, such as Kubernetes.

## Options

| Option | Environment Variable | Default | Description |
|--------|----------------------|---------|-------------|
| `--register` | `WANAKU_REGISTER` | `false` | Turns on self-registration. |
| `--wanaku-url` | `WANAKU_URL` | - | Wanaku management API URL, for example `http://wanaku:8080`. Required with `--register`. |
| `--name` | `SERVICE_NAME` | `camel` | Forward name. Use only letters, digits, `.`, `_` and `-`. Forward names are global in Wanaku: use a different name for each capability. |
| `--namespace` | `WANAKU_NAMESPACE` | `default` | Wanaku namespace of the forward. |
| `--advertised-address` | `MCP_ADDRESS` | - | MCP URL that Wanaku uses to reach this capability, for example `http://my-tool.tools.svc:9090/mcp`. Required with `--register`. |
| `--wanaku-token-env` | `WANAKU_TOKEN_ENV` | - | Name of the environment variable that contains a bearer token for the Wanaku API. The token itself is never a command-line argument. |
| `--retries` | - | `12` | Maximum number of retries when Wanaku cannot be reached or returns a 5xx status. |
| `--wait-seconds` | - | `5` | Wait time between registration attempts, in seconds. |

`--registration-url` is a different option. It sets the Wanaku Barn base URL for downloads. It does not register the capability.

## Lifecycle

On startup, the capability does these steps in sequence:

1. It loads the routes and starts Camel and the MCP server.
2. It connects to its own MCP server at `http://127.0.0.1:<mcp-port>/mcp`. It sends the MCP `initialize` request and the `tools/list` request. It continues only if the server exposes one or more tools.
3. It sends `POST <wanaku-url>/api/v1/forwards?replace=true` with the forward name, the advertised address, the namespace, and these labels:
   - `cic.instance`: a random identifier for this process
   - `cic.version`: the capability version
   - `cic.routes-digest`: the SHA-256 digest of the loaded routes file
4. Wanaku connects to the advertised address to discover the tools. The registration succeeds only if Wanaku reports the forward as available and discovers one or more tools.
5. The readiness probe changes to UP.

The `replace=true` parameter tells Wanaku to replace a forward with the same name. A new instance thus takes over the forward of the instance that it replaces, for example during a Kubernetes rolling update. Without this parameter, Wanaku 0.3.0 and later return HTTP 409 for an existing forward.

If Wanaku cannot be reached or returns a 5xx status, the capability tries again up to `--retries` times.
If registration fails, the capability removes the forward if it owns it, stops Camel, and exits with status 1.
Kubernetes then restarts the pod.

## Shutdown and ownership

When the capability receives a stop signal, it does these steps before it stops Camel:

1. It sets the readiness probe to DOWN.
2. It sends `DELETE <wanaku-url>/api/v1/forwards/<name>?label.cic.instance=<instance>`. Wanaku deletes the forward only if the `cic.instance` label is the identifier of this process.
3. If another instance owns the forward (HTTP 409), it keeps the forward. This prevents an old instance from removing the registration of its replacement.
4. If the forward does not exist (HTTP 404), it does nothing.

Wanaku checks the label and deletes the forward in one operation. A replacement that registers at the same time cannot lose its forward.

Each HTTP call has a 10-second timeout. Set `terminationGracePeriodSeconds` to 30 or more.

## Kubernetes example

Wanaku must reach the advertised address before the pod is ready. A Kubernetes Service sends traffic only to ready pods by default. Set `publishNotReadyAddresses: true` on the Service that the advertised address uses. Otherwise, discovery fails and the pod never becomes ready.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: echo-tool
spec:
  selector:
    app: echo-tool
  publishNotReadyAddresses: true
  ports:
    - name: mcp
      port: 9090
      targetPort: mcp
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: echo-tool
spec:
  replicas: 1
  selector:
    matchLabels:
      app: echo-tool
  template:
    metadata:
      labels:
        app: echo-tool
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: echo-tool
          image: quay.io/wanaku/camel-integration-capability:latest
          env:
            - name: ROUTES_PATH
              value: "file:///routes/echo-tool.camel.yaml"
            - name: SERVICE_NAME
              value: "echo-tool"
            - name: WANAKU_REGISTER
              value: "true"
            - name: WANAKU_URL
              value: "http://wanaku:8080"
            - name: WANAKU_NAMESPACE
              value: "default"
            - name: MCP_ADDRESS
              value: "http://echo-tool.tools.svc:9090/mcp"
            - name: WANAKU_TOKEN_ENV
              value: "WANAKU_TOKEN"
            - name: WANAKU_TOKEN
              valueFrom:
                secretKeyRef:
                  name: wanaku-api-token
                  key: token
          ports:
            - name: mcp
              containerPort: 9090
          livenessProbe:
            httpGet:
              path: /observe/health/live
              port: mcp
            initialDelaySeconds: 15
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /observe/health/ready
              port: mcp
            initialDelaySeconds: 10
            periodSeconds: 5
          volumeMounts:
            - name: routes
              mountPath: /routes
              readOnly: true
      volumes:
        - name: routes
          configMap:
            name: echo-tool-routes
```

Omit `WANAKU_TOKEN_ENV` and `WANAKU_TOKEN` when the Wanaku API does not require authentication.

The `WanakuIntegration` resource of the Wanaku Operator sets up the same registration for you. See [Deploy on Kubernetes](kubernetes.md).

## Next steps

- [Deploy on Kubernetes](kubernetes.md): run CIC with the Wanaku Operator.
- [Operations](production.md): health probes and the `wanaku-registration` readiness check.
- [Troubleshooting](troubleshooting.md#self-registration-failures): find the cause of a failed registration.

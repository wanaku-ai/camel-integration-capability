# Test the echo tool on OpenShift

This guide is for testing the echo tool and exploring the deployment details,
including container configuration, route file mounting, and MCP service access.
For production deployments, use the Wanaku operator as described in
[Deploying the Service](usage.md#deploying-the-service).

The [echo tool manifest](../examples/openshift/echo-tool.yaml) runs
`quay.io/wanaku/camel-integration-capability:latest` with the equivalent of:

```bash
java -Decho.separator=- -jar /app/app.jar \
  --routes-ref file:///routes/wanaku-echo-tool.camel.yaml \
  --data-dir /data --mcp-port 9090
```

The image's Dockerfile uses `/app/app.jar` and reads `ROUTES_PATH`.
`JAVA_TOOL_OPTIONS` supplies the Java system property. The ConfigMap contains
the echo route from `wanaku-barn`; it is mounted as a file inside the pod because
the original macOS file path is not available on OpenShift nodes.
The route echoes `hello` as `hello-hello`.

## Deploy for testing

Log in with `oc login` and select your project with `oc project <project>`.
From this repository's root, run:

```bash
oc apply -f examples/openshift/echo-tool.yaml
oc rollout status deployment/echo-tool --timeout=180s
oc logs deployment/echo-tool
```

The manifest creates a ConfigMap, one Deployment replica, and a Service on port
9090. OpenShift assigns the container UID; no privileged security policy is
required. An `emptyDir` provides writable `/data` storage, which is discarded
when the pod is replaced. No Wanaku router is needed for this local file route.

## Access

For local access to the MCP server:

```bash
oc port-forward service/echo-tool 9090:9090
```

Connect your MCP client to the server at `http://localhost:9090`, using the
transport endpoint supported by the image's Camel MCP server.

To expose the service through an OpenShift HTTPS Route instead:

```bash
oc create route edge echo-tool --service=echo-tool --port=mcp \
  --insecure-policy=Redirect
oc get route echo-tool
```

The Route exposes the echo tool to clients that can reach the cluster ingress;
this example does not configure application authentication.

## Update or remove

After editing the embedded route in the manifest, apply it and restart the pod
so Camel loads the updated route:

```bash
oc apply -f examples/openshift/echo-tool.yaml
oc rollout restart deployment/echo-tool
oc rollout status deployment/echo-tool --timeout=180s
```

The example uses the requested `latest` image with `imagePullPolicy: Always`.
For repeatable deployments, replace it with a release tag or image digest.

To remove the example:

```bash
oc delete route echo-tool --ignore-not-found
oc delete -f examples/openshift/echo-tool.yaml
```

package ai.wanaku.capability.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.HealthProbeGrpc;
import ai.wanaku.core.exchange.v1.HealthProbeReply;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.RuntimeStatus;

/**
 * A health probe that supports deferred target initialization.
 * <p>
 * Unlike {@link ai.wanaku.capabilities.sdk.runtime.camel.grpc.CamelHealthProbe},
 * this probe does not require a valid service target at construction time.
 * Before the target is set, health probes respond with the CamelContext status
 * without validating the request ID. After {@link #setTarget(ServiceTarget)} is
 * called, full ID validation is performed.
 * <p>
 * This allows the gRPC server to start and respond to health checks before
 * registration with the router completes, preventing the race condition where
 * the router's immediate post-registration health check fails because the
 * gRPC server isn't running yet.
 */
public class DeferredHealthProbe extends HealthProbeGrpc.HealthProbeImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(DeferredHealthProbe.class);

    private final CamelContext camelContext;
    private volatile ServiceTarget target;

    public DeferredHealthProbe(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void setTarget(ServiceTarget target) {
        this.target = target;
    }

    RuntimeStatus mapStatus(ServiceStatus serviceStatus) {
        switch (serviceStatus) {
            case Initializing, Initialized, Starting -> {
                return RuntimeStatus.RUNTIME_STATUS_STARTING;
            }
            case Started -> {
                return RuntimeStatus.RUNTIME_STATUS_STARTED;
            }
            default -> {
                return RuntimeStatus.RUNTIME_STATUS_STOPPED;
            }
        }
    }

    @Override
    public void getStatus(HealthProbeRequest request, StreamObserver<HealthProbeReply> responseObserver) {
        ServiceTarget currentTarget = this.target;

        if (currentTarget != null
                && currentTarget.getId() != null
                && !currentTarget.getId().equals(request.getId())) {
            LOG.error(
                    "Requested capability ID ({}) doesn't match existing one {}",
                    request.getId(),
                    currentTarget.getId());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid request id " + request.getId())
                    .asRuntimeException());
        } else {
            RuntimeStatus status = mapStatus(camelContext.getStatus());
            responseObserver.onNext(
                    HealthProbeReply.newBuilder().setStatus(status).build());
            responseObserver.onCompleted();
        }
    }
}

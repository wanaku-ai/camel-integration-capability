package ai.wanaku.capability.camel;

import java.util.ArrayList;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.impl.DefaultCamelContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.HealthProbeReply;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.RuntimeStatus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredHealthProbeIT {

    private static CamelContext camelContext;

    /**
     * A minimal StreamObserver implementation that captures the response, error,
     * and completion state for test assertions.
     */
    private static class TestStreamObserver<T> implements StreamObserver<T> {
        private final List<T> values = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        T getSingleValue() {
            assertEquals(1, values.size(), "Expected exactly one response value");
            return values.get(0);
        }

        Throwable getError() {
            return error;
        }

        boolean isCompleted() {
            return completed;
        }
    }

    @BeforeAll
    static void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterAll
    static void tearDown() {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    // --- mapStatus tests ---

    @Test
    void mapStatusInitializingReturnsStarting() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTING, probe.mapStatus(ServiceStatus.Initializing));
    }

    @Test
    void mapStatusInitializedReturnsStarting() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTING, probe.mapStatus(ServiceStatus.Initialized));
    }

    @Test
    void mapStatusStartingReturnsStarting() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTING, probe.mapStatus(ServiceStatus.Starting));
    }

    @Test
    void mapStatusStartedReturnsStarted() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTED, probe.mapStatus(ServiceStatus.Started));
    }

    @Test
    void mapStatusStoppingReturnsStopped() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STOPPED, probe.mapStatus(ServiceStatus.Stopping));
    }

    @Test
    void mapStatusStoppedReturnsStopped() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STOPPED, probe.mapStatus(ServiceStatus.Stopped));
    }

    @Test
    void mapStatusSuspendingReturnsStopped() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STOPPED, probe.mapStatus(ServiceStatus.Suspending));
    }

    @Test
    void mapStatusSuspendedReturnsStopped() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STOPPED, probe.mapStatus(ServiceStatus.Suspended));
    }

    // --- getStatus tests: no target set ---

    @Test
    void getStatusBeforeTargetSetReturnsStartedStatus() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        HealthProbeRequest request = HealthProbeRequest.newBuilder().build();
        TestStreamObserver<HealthProbeReply> observer = new TestStreamObserver<>();

        probe.getStatus(request, observer);

        assertNull(observer.getError(), "Should not produce an error when no target is set");
        assertTrue(observer.isCompleted(), "Response stream should be completed");
        HealthProbeReply reply = observer.getSingleValue();
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTED, reply.getStatus());
    }

    @Test
    void getStatusBeforeTargetSetWithArbitraryIdStillSucceeds() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        HealthProbeRequest request =
                HealthProbeRequest.newBuilder().setId("some-arbitrary-id").build();
        TestStreamObserver<HealthProbeReply> observer = new TestStreamObserver<>();

        probe.getStatus(request, observer);

        assertNull(observer.getError(), "Should not validate ID when no target is set");
        assertTrue(observer.isCompleted(), "Response stream should be completed");
        HealthProbeReply reply = observer.getSingleValue();
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTED, reply.getStatus());
    }

    // --- getStatus tests: target set with matching ID ---

    @Test
    void getStatusWithMatchingTargetIdReturnsStartedStatus() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        ServiceTarget target = new ServiceTarget(
                "test-capability-123", "test-service", "localhost", 9090, "tool-invoker", null, null, null, null);
        probe.setTarget(target);

        HealthProbeRequest request =
                HealthProbeRequest.newBuilder().setId("test-capability-123").build();
        TestStreamObserver<HealthProbeReply> observer = new TestStreamObserver<>();

        probe.getStatus(request, observer);

        assertNull(observer.getError(), "Should not produce an error for matching ID");
        assertTrue(observer.isCompleted(), "Response stream should be completed");
        HealthProbeReply reply = observer.getSingleValue();
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTED, reply.getStatus());
    }

    // --- getStatus tests: target set with mismatching ID ---

    @Test
    void getStatusWithMismatchingTargetIdReturnsInvalidArgument() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        ServiceTarget target = new ServiceTarget(
                "expected-id", "test-service", "localhost", 9090, "tool-invoker", null, null, null, null);
        probe.setTarget(target);

        HealthProbeRequest request =
                HealthProbeRequest.newBuilder().setId("wrong-id").build();
        TestStreamObserver<HealthProbeReply> observer = new TestStreamObserver<>();

        probe.getStatus(request, observer);

        assertNotNull(observer.getError(), "Should produce an error for mismatching ID");
        assertTrue(observer.getError() instanceof StatusRuntimeException, "Error should be a StatusRuntimeException");
        StatusRuntimeException sre = (StatusRuntimeException) observer.getError();
        assertEquals(Status.INVALID_ARGUMENT.getCode(), sre.getStatus().getCode());
        assertTrue(
                sre.getStatus().getDescription().contains("wrong-id"),
                "Error description should contain the invalid request ID");
    }

    // --- getStatus edge case: target set with null ID ---

    @Test
    void getStatusWithTargetHavingNullIdSkipsValidation() {
        DeferredHealthProbe probe = new DeferredHealthProbe(camelContext);
        // ServiceTarget with null id -- created via newEmptyTarget
        ServiceTarget target = ServiceTarget.newEmptyTarget("test-service", "localhost", 9090, "tool-invoker");
        probe.setTarget(target);

        HealthProbeRequest request =
                HealthProbeRequest.newBuilder().setId("any-id").build();
        TestStreamObserver<HealthProbeReply> observer = new TestStreamObserver<>();

        probe.getStatus(request, observer);

        assertNull(observer.getError(), "Should not validate ID when target has null ID");
        assertTrue(observer.isCompleted(), "Response stream should be completed");
        HealthProbeReply reply = observer.getSingleValue();
        assertEquals(RuntimeStatus.RUNTIME_STATUS_STARTED, reply.getStatus());
    }
}

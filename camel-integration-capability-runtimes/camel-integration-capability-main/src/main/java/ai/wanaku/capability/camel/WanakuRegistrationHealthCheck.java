package ai.wanaku.capability.camel;

import java.util.Map;
import java.util.function.BooleanSupplier;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

/**
 * Readiness check that reports DOWN until the capability is ready to serve: registered with Wanaku when
 * self-registration is on, and not stopping.
 */
class WanakuRegistrationHealthCheck extends AbstractHealthCheck {
    private final BooleanSupplier ready;

    WanakuRegistrationHealthCheck(BooleanSupplier ready) {
        super("wanaku", "wanaku-registration");
        this.ready = ready;
    }

    @Override
    public boolean isLiveness() {
        return false;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        if (ready.getAsBoolean()) {
            builder.up();
        } else {
            builder.down().message("Not registered with Wanaku or stopping");
        }
    }
}

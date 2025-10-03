package ai.wanaku.tool.camel.spec.rules.resources;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.services.ServicesHttpClient;
import ai.wanaku.tool.camel.spec.rules.RulesProcessor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WanakuResourceRuleProcessor implements RulesProcessor<ResourceReference> {
    private static final Logger LOG = LoggerFactory.getLogger(WanakuResourceRuleProcessor.class);

    private final ServicesHttpClient servicesClient;
    private final List<ResourceReference> registered = new CopyOnWriteArrayList<>();

    public WanakuResourceRuleProcessor(ServicesHttpClient servicesClient) {
        this.servicesClient = servicesClient;

        Runtime.getRuntime().addShutdownHook(new Thread(this::deregisterResources));
    }

    @Override
    public void eval(ResourceReference toolReference) {
        try {
            servicesClient.exposeResource(toolReference);
            registered.add(toolReference);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deregisterResources() {
        for (ResourceReference ref : registered) {
            try {
                servicesClient.removeResource(ref.getName());
            } catch (Exception e) {
                LOG.warn("Unable to deregister tool {}: {}", ref.getName(), e.getMessage());
            }
        }
    }
}

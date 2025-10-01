package ai.wanaku.tool.camel.util;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.services.ServicesHttpClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WanakuRuleProcessor implements ToolRulesManager.RulesProcessor<ToolReference> {
    private static final Logger LOG = LoggerFactory.getLogger(WanakuRuleProcessor.class);

    private final ServicesHttpClient servicesClient;
    private final List<ToolReference> registered = new CopyOnWriteArrayList<>();

    public WanakuRuleProcessor(ServicesHttpClient servicesClient) {
        this.servicesClient = servicesClient;

        Runtime.getRuntime().addShutdownHook(new Thread(this::deregisterTools));
    }

    @Override
    public void eval(ToolReference toolReference) {
        servicesClient.addTool(toolReference);
        registered.add(toolReference);
    }

    private void deregisterTools() {
        for (ToolReference ref : registered) {
            try {
                servicesClient.removeTool(ref.getName());
            } catch (Exception e) {
                LOG.warn("Unable to deregister tool {}: {}", ref.getName(), e.getMessage());
            }
        }
    }
}

package ai.wanaku.tool.camel.spec.rules.tools;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.services.ServicesHttpClient;
import ai.wanaku.tool.camel.spec.rules.RulesProcessor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WanakuToolRuleProcessor implements RulesProcessor<ToolReference> {
    private static final Logger LOG = LoggerFactory.getLogger(WanakuToolRuleProcessor.class);

    private final ServicesHttpClient servicesClient;
    private final List<ToolReference> registered = new CopyOnWriteArrayList<>();

    public WanakuToolRuleProcessor(ServicesHttpClient servicesClient) {
        this.servicesClient = servicesClient;

        Runtime.getRuntime().addShutdownHook(new Thread(this::deregisterTools));
    }

    @Override
    public void eval(ToolReference toolReference) {
        try {
            servicesClient.addTool(toolReference);
            registered.add(toolReference);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

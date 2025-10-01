package ai.wanaku.tool.camel.util;

import ai.wanaku.tool.camel.model.Tool;
import ai.wanaku.tool.camel.model.ToolDefinition;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToolRulesManager {
    private static final Logger LOG = LoggerFactory.getLogger(ToolRulesManager.class);
    private final String name;
    private final String routesRules;

    @FunctionalInterface
    public interface RulesTransformer <T> {
        T transform(String name, ToolDefinition toolDefinition);
    }

    @FunctionalInterface
    public interface RulesProcessor<T> {
        void eval(T rule);
    }

    public ToolRulesManager(String name, String routesRules) {
        this.name = name;
        this.routesRules = routesRules;
    }

    private Tool loadToolRules() {
        if (routesRules == null || routesRules.isEmpty()) {
            LOG.warn("No routes rules file specified");
            return null;
        }

        try {
            Tool loadedTool = ToolYamlReader.readFromFile(routesRules);
            LOG.info("Successfully loaded tool rules from: {}", routesRules);
            return loadedTool;
        } catch (IOException e) {
            LOG.error("Failed to load tool rules from: {}", routesRules, e);
            throw new RuntimeException("Failed to load tool rules", e);
        }
    }


    public <T> void registerTools(Tool tool, RulesTransformer<T> transformer, RulesProcessor<T> processor) {
        final Map<String, ToolDefinition> tools = tool.getTools();

        for (Map.Entry<String, ToolDefinition> entry : tools.entrySet()) {
            final ToolDefinition toolDef = entry.getValue();
            T transformed = transformer.transform(entry.getKey(), toolDef);
            processor.eval(transformed);

        }
    }

    public <T> Tool loadTools(RulesTransformer<T> transformer, RulesProcessor<T> processor) {
        Tool tool = loadToolRules();
        if (tool != null) {
            registerTools(tool, transformer, processor);
        } else {
            LOG.warn("No tool registered for {}", name);
        }

        return tool;
    }
}

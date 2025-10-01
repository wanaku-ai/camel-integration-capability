package ai.wanaku.tool.camel.util;

import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.tool.camel.model.Property;
import ai.wanaku.tool.camel.model.ToolDefinition;
import java.util.ArrayList;
import java.util.List;

public class WanakuToolTransformer implements ToolRulesManager.RulesTransformer<ToolReference> {
    public static final String DEFAULT_INPUT_SCHEMA_TYPE = "object";
    private final String name;
    private List<String> required = new ArrayList<>();

    public WanakuToolTransformer(String name) {
        this.name = name;
    }

    @Override
    public ToolReference transform(String ruleName, ToolDefinition toolDefinition) {
        ToolReference toolReference = new ToolReference();

        toolReference.setName(ruleName);
        toolReference.setDescription(toolDefinition.getDescription());
        toolReference.setUri(String.format("%s://%s", name, ruleName));
        toolReference.setType(name);

        InputSchema inputSchema = new InputSchema();
        inputSchema.setType(DEFAULT_INPUT_SCHEMA_TYPE);

        final List<Property> properties = toolDefinition.getProperties();
        for (Property property : properties) {
            ai.wanaku.api.types.Property wanakuProperty = new ai.wanaku.api.types.Property();
            wanakuProperty.setType(property.getType());
            wanakuProperty.setDescription(property.getDescription());

            if (property.isRequired()) {
                required.add(property.getName());
            }

            inputSchema.getProperties().put(property.getName(), wanakuProperty);
        }

        toolReference.setInputSchema(inputSchema);

        if (!required.isEmpty()) {
            toolReference.getInputSchema().setRequired(required);
        }

        return toolReference;
    }
}

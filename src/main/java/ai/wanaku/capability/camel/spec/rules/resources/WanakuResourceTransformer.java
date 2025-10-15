package ai.wanaku.capability.camel.spec.rules.resources;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.capability.camel.model.Definition;
import ai.wanaku.capability.camel.spec.rules.RulesTransformer;

public class WanakuResourceTransformer implements RulesTransformer {
    private final String name;
    private final WanakuResourceRuleProcessor processor;

    public WanakuResourceTransformer(String name, WanakuResourceRuleProcessor processor) {
        this.name = name;
        this.processor = processor;
    }

    @Override
    public void transform(String ruleName, Definition toolDefinition) {
        ResourceReference resourceReference = new ResourceReference();

        resourceReference.setName(ruleName);
        resourceReference.setDescription(toolDefinition.getDescription());
        resourceReference.setLocation(String.format("%s://%s", name, ruleName));
        resourceReference.setType(name);

        processor.eval(resourceReference);
    }
}

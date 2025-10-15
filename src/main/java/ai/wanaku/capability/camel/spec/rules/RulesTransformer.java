package ai.wanaku.capability.camel.spec.rules;

import ai.wanaku.capability.camel.model.Definition;

@FunctionalInterface
public interface RulesTransformer {
    void transform(String name, Definition definition);
}

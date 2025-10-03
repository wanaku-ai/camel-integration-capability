package ai.wanaku.tool.camel.spec.rules;

import ai.wanaku.tool.camel.model.Definition;

@FunctionalInterface
public interface RulesTransformer {
    void transform(String name, Definition definition);
}

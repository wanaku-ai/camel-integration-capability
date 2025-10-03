package ai.wanaku.tool.camel.spec.rules;

@FunctionalInterface
public interface RulesProcessor <T> {
    void eval(T rule);
}

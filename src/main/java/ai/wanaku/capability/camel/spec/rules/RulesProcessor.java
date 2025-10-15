package ai.wanaku.capability.camel.spec.rules;

@FunctionalInterface
public interface RulesProcessor <T> {
    void eval(T rule);
}

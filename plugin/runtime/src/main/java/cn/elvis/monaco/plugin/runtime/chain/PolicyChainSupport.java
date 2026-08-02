package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.HookBinding;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

final class PolicyChainSupport {

    private PolicyChainSupport() {
    }

    static <H extends PluginHook, T> Mono<PolicyDecision<T>> invoke(
            List<HookBinding<H>> hooks,
            PluginRequestContext context,
            T initial,
            InvocationBudget budget,
            String operation,
            BiFunction<H, T, Mono<PolicyDecision<T>>> invocation,
            BiPredicate<T, T> validChange
    ) {
        return invoke(hooks, 0, context, initial, budget, operation, invocation, validChange, Map.of())
                .onErrorReturn(reject());
    }

    private static <H extends PluginHook, T> Mono<PolicyDecision<T>> invoke(
            List<HookBinding<H>> hooks,
            int index,
            PluginRequestContext context,
            T current,
            InvocationBudget budget,
            String operation,
            BiFunction<H, T, Mono<PolicyDecision<T>>> invocation,
            BiPredicate<T, T> validChange,
            Map<String, String> attributes
    ) {
        if (index == hooks.size()) {
            return Mono.just(PolicyDecision.allow(current, attributes));
        }
        HookBinding<H> binding = hooks.get(index);
        return ChainSupport.invoke(
                        binding,
                        operation,
                        budget,
                        () -> invocation.apply(binding.hook(), current))
                .switchIfEmpty(Mono.just(reject()))
                .flatMap(decision -> {
                    if (decision instanceof PolicyDecision.Reject<T>) {
                        return Mono.just(decision);
                    }
                    PolicyDecision.Allow<T> allow = (PolicyDecision.Allow<T>) decision;
                    if (!validChange.test(current, allow.value())) {
                        return Mono.error(new PluginRuntimeException(
                                "Plugin returned a forbidden " + operation + " modification: "
                                        + binding.plugin().descriptor().id()));
                    }
                    Map<String, String> merged = mergeAttributes(
                            attributes,
                            binding.plugin().descriptor().id(),
                            allow.attributes());
                    return invoke(
                            hooks,
                            index + 1,
                            context,
                            allow.value(),
                            budget,
                            operation,
                            invocation,
                            validChange,
                            merged);
                });
    }

    private static Map<String, String> mergeAttributes(
            Map<String, String> existing,
            String pluginId,
            Map<String, String> additions
    ) {
        if (additions.size() > 32) {
            throw new PluginRuntimeException("Plugin returned too many policy attributes: " + pluginId);
        }
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(existing);
        additions.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}") || value == null
                    || value.getBytes(StandardCharsets.UTF_8).length > 1_024) {
                throw new PluginRuntimeException("Plugin returned an invalid policy attribute: " + pluginId);
            }
            merged.put(pluginId + '.' + key, value);
        });
        return Map.copyOf(merged);
    }

    private static <T> PolicyDecision<T> reject() {
        return PolicyDecision.reject(PluginRejectReason.IMPLEMENTATION_ERROR, "");
    }
}

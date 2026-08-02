package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.HookBinding;
import cn.elvis.monaco.plugin.runtime.spi.HookInvocation;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

final class ChainSupport {

    private ChainSupport() {
    }

    static <H extends PluginHook, R> Mono<R> invoke(
            HookBinding<H> binding,
            String operation,
            InvocationBudget budget,
            Supplier<? extends Mono<? extends R>> action
    ) {
        return Mono.defer(() -> {
            Duration timeout = budget.limit(binding.plugin().deployment().decisionTimeout());
            return binding.plugin().invoker().invoke(new HookInvocation<>(
                    binding.hook().hookId(), operation, timeout, action));
        });
    }
}

package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.hook.AuthenticationProvider;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.HookBinding;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/** First-applicable authentication chain with fail-closed invocation errors. */
public final class AuthenticationChain {

    private final HookRegistry registry;
    private final AuthenticationFallback fallback;

    public AuthenticationChain(HookRegistry registry, AuthenticationFallback fallback) {
        if (registry == null || fallback == null) {
            throw new IllegalArgumentException("Authentication chain components must not be null");
        }
        this.registry = registry;
        this.fallback = fallback;
    }

    public Mono<AuthenticationDecision> authenticate(
            PluginRequestContext context,
            AuthenticationRequest request,
            Duration chainTimeout
    ) {
        return Mono.defer(() -> invoke(
                registry.snapshot().authentication(),
                0,
                context,
                request,
                InvocationBudget.start(chainTimeout)))
                .onErrorReturn(reject());
    }

    private Mono<AuthenticationDecision> invoke(
            List<HookBinding<AuthenticationProvider>> hooks,
            int index,
            PluginRequestContext context,
            AuthenticationRequest request,
            InvocationBudget budget
    ) {
        if (index == hooks.size()) {
            return Mono.defer(() -> fallback.authenticate(context, request))
                    .switchIfEmpty(Mono.just(reject()));
        }
        HookBinding<AuthenticationProvider> binding = hooks.get(index);
        return ChainSupport.invoke(
                        binding,
                        "authenticate",
                        budget,
                        () -> binding.hook().authenticate(context, request))
                .switchIfEmpty(Mono.just(reject()))
                .flatMap(decision -> decision instanceof AuthenticationDecision.Abstain
                        ? invoke(hooks, index + 1, context, request, budget)
                        : Mono.just(decision));
    }

    private static AuthenticationDecision reject() {
        return AuthenticationDecision.reject(PluginRejectReason.IMPLEMENTATION_ERROR, "");
    }
}

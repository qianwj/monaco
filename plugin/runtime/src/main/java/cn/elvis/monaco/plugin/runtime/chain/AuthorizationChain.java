package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthorizationDecision;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.hook.AuthorizationPolicy;
import cn.elvis.monaco.plugin.api.model.AuthorizationRequest;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.HookBinding;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/** Deny-overrides authorization chain with injectable Broker fallback. */
public final class AuthorizationChain {

    private final HookRegistry registry;
    private final AuthorizationFallback fallback;

    public AuthorizationChain(HookRegistry registry, AuthorizationFallback fallback) {
        if (registry == null || fallback == null) {
            throw new IllegalArgumentException("Authorization chain components must not be null");
        }
        this.registry = registry;
        this.fallback = fallback;
    }

    public Mono<AuthorizationDecision> authorize(
            PluginRequestContext context,
            AuthorizationRequest request,
            Duration chainTimeout
    ) {
        return Mono.defer(() -> invoke(
                registry.snapshot().authorization(),
                0,
                false,
                context,
                request,
                InvocationBudget.start(chainTimeout)))
                .onErrorReturn(deny());
    }

    private Mono<AuthorizationDecision> invoke(
            List<HookBinding<AuthorizationPolicy>> hooks,
            int index,
            boolean allowed,
            PluginRequestContext context,
            AuthorizationRequest request,
            InvocationBudget budget
    ) {
        if (index == hooks.size()) {
            return allowed
                    ? Mono.just(AuthorizationDecision.allow())
                    : Mono.defer(() -> fallback.authorize(context, request))
                            .switchIfEmpty(Mono.just(deny()));
        }
        HookBinding<AuthorizationPolicy> binding = hooks.get(index);
        return ChainSupport.invoke(
                        binding,
                        "authorize",
                        budget,
                        () -> binding.hook().authorize(context, request))
                .switchIfEmpty(Mono.just(deny()))
                .flatMap(decision -> {
                    if (decision instanceof AuthorizationDecision.Deny) {
                        return Mono.just(decision);
                    }
                    return invoke(
                            hooks,
                            index + 1,
                            allowed || decision instanceof AuthorizationDecision.Allow,
                            context,
                            request,
                            budget);
                });
    }

    private static AuthorizationDecision deny() {
        return AuthorizationDecision.deny(PluginRejectReason.IMPLEMENTATION_ERROR, "");
    }
}

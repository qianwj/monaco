package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.EnhancedAuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.model.EnhancedAuthenticationRequest;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.EnhancedAuthBinding;
import cn.elvis.monaco.plugin.runtime.registry.EnhancedAuthBindingRegistry;
import cn.elvis.monaco.plugin.runtime.registry.HookBinding;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import cn.elvis.monaco.plugin.runtime.registry.HookSnapshot;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** MQTT 5 AUTH exchange with provider affinity across packets. */
public final class EnhancedAuthenticationChain {

    private final HookRegistry hooks;
    private final EnhancedAuthBindingRegistry bindings;

    public EnhancedAuthenticationChain(
            HookRegistry hooks,
            EnhancedAuthBindingRegistry bindings
    ) {
        if (hooks == null || bindings == null) {
            throw new IllegalArgumentException("Enhanced authentication chain components must not be null");
        }
        this.hooks = hooks;
        this.bindings = bindings;
    }

    public Mono<EnhancedAuthenticationDecision> exchange(
            PluginRequestContext context,
            EnhancedAuthenticationRequest request,
            Duration chainTimeout
    ) {
        return Mono.defer(() -> {
            HookSnapshot snapshot = hooks.snapshot();
            EnhancedAuthBinding binding = select(context, request, snapshot);
            if (binding == null) {
                return Mono.just(reject(PluginRejectReason.BAD_AUTHENTICATION_METHOD));
            }
            return ChainSupport.invoke(
                            binding.provider(),
                            "enhanced-authentication",
                            InvocationBudget.start(chainTimeout),
                            () -> binding.provider().hook().exchange(context, request))
                    .switchIfEmpty(Mono.just(reject(PluginRejectReason.IMPLEMENTATION_ERROR)))
                    .flatMap(decision -> validateAndComplete(context, request, decision))
                    .onErrorResume(failure -> {
                        bindings.clear(context.connectionId());
                        return Mono.just(reject(PluginRejectReason.IMPLEMENTATION_ERROR));
                    });
        });
    }

    public void connectionClosed(cn.elvis.monaco.protocol.model.ConnectionId connectionId) {
        bindings.clear(connectionId);
    }

    private EnhancedAuthBinding select(
            PluginRequestContext context,
            EnhancedAuthenticationRequest request,
            HookSnapshot snapshot
    ) {
        EnhancedAuthBinding existing = bindings.find(context.connectionId()).orElse(null);
        if (existing != null) {
            if (existing.hookGeneration() != snapshot.generation()
                    || !existing.authenticationMethod().equals(request.authenticationMethod())) {
                bindings.clear(context.connectionId());
                return null;
            }
            return existing;
        }
        HookBinding<cn.elvis.monaco.plugin.api.hook.EnhancedAuthenticationProvider> provider =
                snapshot.enhancedAuthentication().get(request.authenticationMethod());
        if (provider == null) {
            return null;
        }
        EnhancedAuthBinding created = new EnhancedAuthBinding(
                context.connectionId(),
                request.authenticationMethod(),
                snapshot.generation(),
                provider);
        return bindings.bind(created);
    }

    private Mono<EnhancedAuthenticationDecision> validateAndComplete(
            PluginRequestContext context,
            EnhancedAuthenticationRequest request,
            EnhancedAuthenticationDecision decision
    ) {
        if (decision instanceof EnhancedAuthenticationDecision.Continue continuation) {
            if (!request.authenticationMethod().equals(continuation.authenticationMethod())) {
                bindings.clear(context.connectionId());
                return Mono.just(reject(PluginRejectReason.BAD_AUTHENTICATION_METHOD));
            }
            return Mono.just(decision);
        }
        bindings.clear(context.connectionId());
        return Mono.just(decision);
    }

    private static EnhancedAuthenticationDecision reject(PluginRejectReason reason) {
        return EnhancedAuthenticationDecision.reject(reason, "");
    }
}

package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.WillView;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Sequential Will interceptor chain evaluated only while accepting CONNECT. */
public final class WillInterceptorChain {

    private final HookRegistry registry;

    public WillInterceptorChain(HookRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
    }

    public Mono<PolicyDecision<WillView>> intercept(
            PluginRequestContext context,
            WillView will,
            Duration chainTimeout
    ) {
        return Mono.defer(() -> PolicyChainSupport.invoke(
                registry.snapshot().will(),
                context,
                will,
                InvocationBudget.start(chainTimeout),
                "will-intercept",
                (hook, value) -> hook.intercept(context, value),
                WillInterceptorChain::validChange));
    }

    private static boolean validChange(WillView current, WillView candidate) {
        return candidate.qos().value() <= current.qos().value()
                && current.retain() == candidate.retain()
                && current.delayInterval().equals(candidate.delayInterval())
                && current.messageExpiryInterval().equals(candidate.messageExpiryInterval())
                && current.contentType().equals(candidate.contentType())
                && current.responseTopic().equals(candidate.responseTopic())
                && current.correlationData().equals(candidate.correlationData());
    }
}

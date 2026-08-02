package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.PublishView;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Sequential inbound publish interceptor chain. */
public final class PublishInterceptorChain {

    private final HookRegistry registry;

    public PublishInterceptorChain(HookRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
    }

    public Mono<PolicyDecision<PublishView>> intercept(
            PluginRequestContext context,
            PublishView publication,
            Duration chainTimeout
    ) {
        return Mono.defer(() -> PolicyChainSupport.invoke(
                registry.snapshot().publishInbound(),
                context,
                publication,
                InvocationBudget.start(chainTimeout),
                "publish-intercept",
                (hook, value) -> hook.intercept(context, value),
                PublishInterceptorChain::validChange));
    }

    private static boolean validChange(PublishView current, PublishView candidate) {
        return candidate.qos().value() <= current.qos().value()
                && current.retain() == candidate.retain()
                && current.duplicate() == candidate.duplicate()
                && current.packetId().equals(candidate.packetId())
                && current.messageExpiryInterval().equals(candidate.messageExpiryInterval())
                && current.contentType().equals(candidate.contentType())
                && current.responseTopic().equals(candidate.responseTopic())
                && current.correlationData().equals(candidate.correlationData())
                && current.origin() == candidate.origin();
    }
}

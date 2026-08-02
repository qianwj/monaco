package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.SubscriptionView;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Sequential chain for one SUBSCRIBE entry. */
public final class SubscriptionInterceptorChain {

    private final HookRegistry registry;

    public SubscriptionInterceptorChain(HookRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
    }

    public Mono<PolicyDecision<SubscriptionView>> intercept(
            PluginRequestContext context,
            SubscriptionView subscription,
            Duration chainTimeout
    ) {
        return Mono.defer(() -> PolicyChainSupport.invoke(
                registry.snapshot().subscription(),
                context,
                subscription,
                InvocationBudget.start(chainTimeout),
                "subscription-intercept",
                (hook, value) -> hook.intercept(context, value),
                SubscriptionInterceptorChain::validChange));
    }

    private static boolean validChange(SubscriptionView current, SubscriptionView candidate) {
        return candidate.maximumQos().value() <= current.maximumQos().value()
                && current.topicFilter().equals(candidate.topicFilter())
                && current.noLocal() == candidate.noLocal()
                && current.retainAsPublished() == candidate.retainAsPublished()
                && current.retainHandling() == candidate.retainHandling()
                && current.subscriptionIdentifiers().equals(candidate.subscriptionIdentifiers());
    }
}

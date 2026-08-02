package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.ConnectView;
import cn.elvis.monaco.plugin.runtime.invoke.InvocationBudget;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Sequential connection interceptor chain. */
public final class ConnectionInterceptorChain {

    private final HookRegistry registry;

    public ConnectionInterceptorChain(HookRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
    }

    public Mono<PolicyDecision<ConnectView>> intercept(
            PluginRequestContext context,
            ConnectView connection,
            Duration chainTimeout
    ) {
        return Mono.defer(() -> PolicyChainSupport.invoke(
                registry.snapshot().connection(),
                context,
                connection,
                InvocationBudget.start(chainTimeout),
                "connection-intercept",
                (hook, value) -> hook.intercept(context, value),
                ConnectionInterceptorChain::validChange));
    }

    private static boolean validChange(ConnectView current, ConnectView candidate) {
        return current.clientId().equals(candidate.clientId())
                && current.cleanStart() == candidate.cleanStart()
                && current.sessionExpiryInterval().equals(candidate.sessionExpiryInterval())
                && current.keepAliveSeconds() == candidate.keepAliveSeconds()
                && current.receiveMaximum() == candidate.receiveMaximum()
                && current.maximumPacketSize() == candidate.maximumPacketSize()
                && current.topicAliasMaximum() == candidate.topicAliasMaximum();
    }
}

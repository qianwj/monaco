package cn.elvis.monaco.plugin.api.hook;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.PublishView;
import reactor.core.publisher.Mono;

/** Inbound PUBLISH interceptor invoked once before persistence. */
public interface PublishInboundInterceptor extends PluginHook {

    Mono<PolicyDecision<PublishView>> intercept(
            PluginRequestContext context,
            PublishView publication);
}

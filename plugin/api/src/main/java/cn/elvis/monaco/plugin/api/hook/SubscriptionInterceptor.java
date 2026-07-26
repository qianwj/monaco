package cn.elvis.monaco.plugin.api.hook;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.SubscriptionView;
import reactor.core.publisher.Mono;

/** SUBSCRIBE interceptor invoked independently for each request entry. */
public interface SubscriptionInterceptor extends PluginHook {

    Mono<PolicyDecision<SubscriptionView>> intercept(
            PluginRequestContext context,
            SubscriptionView subscription);
}

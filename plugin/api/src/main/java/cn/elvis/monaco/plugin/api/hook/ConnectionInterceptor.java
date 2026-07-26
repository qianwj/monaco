package cn.elvis.monaco.plugin.api.hook;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.ConnectView;
import reactor.core.publisher.Mono;

/** Connection policy invoked after authentication and before session mutation. */
public interface ConnectionInterceptor extends PluginHook {

    Mono<PolicyDecision<ConnectView>> intercept(
            PluginRequestContext context,
            ConnectView connection);
}

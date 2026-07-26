package cn.elvis.monaco.plugin.api.hook;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.WillView;
import reactor.core.publisher.Mono;

/** Will interceptor invoked once while accepting CONNECT, never at Will delivery. */
public interface WillInterceptor extends PluginHook {

    Mono<PolicyDecision<WillView>> intercept(
            PluginRequestContext context,
            WillView will);
}

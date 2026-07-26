package cn.elvis.monaco.plugin.api.hook;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthorizationDecision;
import cn.elvis.monaco.plugin.api.model.AuthorizationRequest;
import reactor.core.publisher.Mono;

/** Authorization policy; runtime merging is deny-overrides. */
public interface AuthorizationPolicy extends PluginHook {

    Mono<AuthorizationDecision> authorize(
            PluginRequestContext context,
            AuthorizationRequest request);
}

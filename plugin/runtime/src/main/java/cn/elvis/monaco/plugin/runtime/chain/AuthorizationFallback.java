package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthorizationDecision;
import cn.elvis.monaco.plugin.api.model.AuthorizationRequest;
import reactor.core.publisher.Mono;

/** Default Broker authorization used when every plugin abstains. */
@FunctionalInterface
public interface AuthorizationFallback {

    Mono<AuthorizationDecision> authorize(
            PluginRequestContext context,
            AuthorizationRequest request);
}

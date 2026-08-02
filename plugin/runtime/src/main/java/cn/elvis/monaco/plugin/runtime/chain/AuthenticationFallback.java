package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import reactor.core.publisher.Mono;

/** Default Broker authentication used when every plugin abstains. */
@FunctionalInterface
public interface AuthenticationFallback {

    Mono<AuthenticationDecision> authenticate(
            PluginRequestContext context,
            AuthenticationRequest request);
}

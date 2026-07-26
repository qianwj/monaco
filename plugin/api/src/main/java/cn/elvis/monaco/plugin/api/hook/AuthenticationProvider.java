package cn.elvis.monaco.plugin.api.hook;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import reactor.core.publisher.Mono;

/** Basic CONNECT authentication provider; runtime merging is first-applicable. */
public interface AuthenticationProvider extends PluginHook {

    Mono<AuthenticationDecision> authenticate(
            PluginRequestContext context,
            AuthenticationRequest request);
}

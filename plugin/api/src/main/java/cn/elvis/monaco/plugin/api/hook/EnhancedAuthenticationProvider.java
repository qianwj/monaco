package cn.elvis.monaco.plugin.api.hook;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.EnhancedAuthenticationDecision;
import cn.elvis.monaco.plugin.api.model.EnhancedAuthenticationRequest;
import reactor.core.publisher.Mono;

/** Provider for one pinned MQTT 5 enhanced-authentication exchange. */
public interface EnhancedAuthenticationProvider extends PluginHook {

    /** Authentication method claimed by this provider, for example SCRAM-SHA-256. */
    String authenticationMethod();

    Mono<EnhancedAuthenticationDecision> exchange(
            PluginRequestContext context,
            EnhancedAuthenticationRequest request);
}

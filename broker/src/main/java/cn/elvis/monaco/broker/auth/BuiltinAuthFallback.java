package cn.elvis.monaco.broker.auth;

import cn.elvis.monaco.core.auth.CoreAuthenticator;
import cn.elvis.monaco.core.auth.CoreAuthenticator.AuthResult;
import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.runtime.chain.AuthenticationFallback;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

/**
 * Adapts core's {@link CoreAuthenticator} as the plugin chain's
 * {@link AuthenticationFallback}. Invoked when no auth plugin handles the request.
 */
public final class BuiltinAuthFallback implements AuthenticationFallback {

    private final CoreAuthenticator authenticator;
    private final boolean authEnabled;

    private BuiltinAuthFallback(CoreAuthenticator authenticator, boolean authEnabled) {
        this.authenticator = authenticator;
        this.authEnabled = authEnabled;
    }

    public static BuiltinAuthFallback create(String authMode, String authFilePath) {
        if ("none".equals(authMode)) {
            return new BuiltinAuthFallback(null, false);
        }
        return new BuiltinAuthFallback(CoreAuthenticator.create(authMode, authFilePath), true);
    }

    @Override
    public Mono<AuthenticationDecision> authenticate(
            PluginRequestContext context,
            AuthenticationRequest request) {
        if (!authEnabled) {
            return Mono.just(AuthenticationDecision.success(
                    new PluginPrincipal("anonymous", Set.of(), Map.of())));
        }
        String username = request.username().orElse(null);
        byte[] password = request.password().isEmpty() ? null : request.password().copyBytes();
        return authenticator.authenticate(username, password)
                .map(result -> switch (result) {
                    case AuthResult.Success s -> AuthenticationDecision.success(
                            new PluginPrincipal(s.username(), Set.of(), Map.of()));
                    case AuthResult.Reject r -> AuthenticationDecision.reject(
                            PluginRejectReason.BAD_CREDENTIALS, r.reason());
                });
    }
}

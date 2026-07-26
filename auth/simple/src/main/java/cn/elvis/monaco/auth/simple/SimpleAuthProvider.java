package cn.elvis.monaco.auth.simple;

import cn.elvis.monaco.auth.credential.CredentialProvider;
import cn.elvis.monaco.auth.credential.PasswordEncoder;
import cn.elvis.monaco.auth.credential.StoredCredential;
import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.hook.AuthenticationProvider;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Username/Password authentication provider.
 * Looks up credentials via {@link CredentialProvider} and verifies with {@link PasswordEncoder}.
 */
public final class SimpleAuthProvider implements AuthenticationProvider {

    private static final String HOOK_ID = "simple-auth";

    private final CredentialProvider credentialProvider;
    private final PasswordEncoder passwordEncoder;

    public SimpleAuthProvider(CredentialProvider credentialProvider, PasswordEncoder passwordEncoder) {
        this.credentialProvider = credentialProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hookId() {
        return HOOK_ID;
    }

    @Override
    public Mono<AuthenticationDecision> authenticate(
            PluginRequestContext context,
            AuthenticationRequest request) {

        if (request.username().isEmpty()) {
            return Mono.just(AuthenticationDecision.abstain());
        }

        String username = request.username().get();
        byte[] passwordBytes = request.password().copyBytes();
        String rawPassword = new String(passwordBytes, StandardCharsets.UTF_8);

        return credentialProvider.lookup(username)
                .map(stored -> verify(username, rawPassword, stored))
                .defaultIfEmpty(AuthenticationDecision.reject(
                        PluginRejectReason.BAD_CREDENTIALS,
                        "Unknown user"));
    }

    private AuthenticationDecision verify(String username, String rawPassword, StoredCredential stored) {
        if (passwordEncoder.matches(rawPassword, stored)) {
            PluginPrincipal principal = new PluginPrincipal(
                    username, Set.of(), stored.attributes());
            return AuthenticationDecision.success(principal);
        }
        return AuthenticationDecision.reject(
                PluginRejectReason.BAD_CREDENTIALS,
                "Invalid credentials");
    }
}

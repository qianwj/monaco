package cn.elvis.monaco.core.auth;

import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Map;

/**
 * Core built-in authenticator. Validates username/password credentials
 * using a configured CredentialProvider and PasswordEncoder.
 *
 * <p>This is NOT coupled to the plugin system. The broker module adapts
 * this as an {@code AuthenticationFallback} for the plugin chain.
 */
public final class CoreAuthenticator {

    private static final Map<String, PasswordEncoder> ENCODERS = Map.of(
            "plain", new PlainPasswordEncoder(),
            "sha256", new Sha256PasswordEncoder()
    );

    private final CredentialProvider credentialProvider;

    private CoreAuthenticator(CredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /** Create authenticator based on config. */
    public static CoreAuthenticator create(String authMode, String authFilePath) {
        CredentialProvider provider = switch (authMode) {
            case "env" -> new EnvCredentialProvider();
            case "file" -> new FileCredentialProvider(Path.of(authFilePath));
            default -> throw new IllegalArgumentException("Unknown auth mode: " + authMode);
        };
        return new CoreAuthenticator(provider);
    }

    /** Create with a custom credential provider (for testing). */
    public static CoreAuthenticator create(CredentialProvider provider) {
        return new CoreAuthenticator(provider);
    }

    /**
     * Authenticate a username/password pair.
     *
     * @return {@code Mono<AuthResult>} — success with username, or reject with reason
     */
    public Mono<AuthResult> authenticate(String username, byte[] password) {
        if (username == null || username.isBlank()) {
            return Mono.just(AuthResult.reject("Missing username"));
        }
        if (password == null || password.length == 0) {
            return Mono.just(AuthResult.reject("Missing password"));
        }
        String rawPassword = new String(password);
        return credentialProvider.lookup(username)
                .map(stored -> {
                    PasswordEncoder encoder = ENCODERS.get(stored.encoderType());
                    if (encoder == null) {
                        return AuthResult.reject("Unsupported encoder: " + stored.encoderType());
                    }
                    if (encoder.matches(rawPassword, stored)) {
                        return AuthResult.success(username);
                    }
                    return AuthResult.reject("Bad credentials");
                })
                .defaultIfEmpty(AuthResult.reject("Unknown user"));
    }

    /** Authentication result — independent of plugin:api types. */
    public sealed interface AuthResult {
        record Success(String username) implements AuthResult {}
        record Reject(String reason) implements AuthResult {}

        static AuthResult success(String username) { return new Success(username); }
        static AuthResult reject(String reason) { return new Reject(reason); }
    }
}

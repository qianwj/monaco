package cn.elvis.monaco.core.auth;

import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Reads credentials from environment variables.
 * Convention: MQTT_USER_&lt;USERNAME_UPPERCASE&gt;=&lt;PASSWORD&gt;
 */
public final class EnvCredentialProvider implements CredentialProvider {

    private static final String PREFIX = "MQTT_USER_";

    private final Function<String, String> envLookup;

    public EnvCredentialProvider() {
        this(System::getenv);
    }

    /** Test-friendly constructor. */
    public EnvCredentialProvider(Function<String, String> envLookup) {
        this.envLookup = envLookup;
    }

    @Override
    public Mono<StoredCredential> lookup(String username) {
        if (username == null || username.isBlank()) {
            return Mono.empty();
        }
        String key = PREFIX + username.toUpperCase();
        String password = envLookup.apply(key);
        if (password == null) {
            return Mono.empty();
        }
        return Mono.just(new StoredCredential(username, password, "plain"));
    }
}

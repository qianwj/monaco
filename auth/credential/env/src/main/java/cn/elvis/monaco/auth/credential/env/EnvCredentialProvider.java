package cn.elvis.monaco.auth.credential.env;

import cn.elvis.monaco.auth.credential.CredentialProvider;
import cn.elvis.monaco.auth.credential.StoredCredential;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

/**
 * Reads credentials from environment variables.
 *
 * <p>Convention: {@code MQTT_USER_<USERNAME>=<PASSWORD>}.
 * Username matching is case-insensitive (env var names are uppercased).</p>
 */
public class EnvCredentialProvider implements CredentialProvider {

    private static final String PREFIX = "MQTT_USER_";

    private final Function<String, String> envLookup;

    /** Production constructor — reads from {@link System#getenv()}. */
    public EnvCredentialProvider() {
        this(System::getenv);
    }

    /** Test constructor — allows injecting a custom env lookup function. */
    EnvCredentialProvider(Function<String, String> envLookup) {
        this.envLookup = envLookup;
    }

    @Override
    public String type() {
        return "env";
    }

    @Override
    public Mono<StoredCredential> lookup(String username) {
        if (username == null || username.isBlank()) {
            return Mono.empty();
        }
        String envKey = PREFIX + username.toUpperCase();
        String password = envLookup.apply(envKey);
        if (password == null) {
            return Mono.empty();
        }
        return Mono.just(StoredCredential.plainText(username, password));
    }
}

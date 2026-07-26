package cn.elvis.monaco.auth.simple;

import cn.elvis.monaco.auth.credential.CredentialProvider;
import cn.elvis.monaco.auth.credential.PasswordEncoder;
import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.descriptor.PluginCapability;
import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.api.lifecycle.PluginContext;
import cn.elvis.monaco.plugin.api.support.PluginConfig;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Simple username/password authentication plugin.
 *
 * <p>Config keys:
 * <ul>
 *   <li>{@code credential.provider} — credential provider type (default: "env")</li>
 *   <li>{@code password.encoder} — "plain" or "sha256" (default: "plain")</li>
 * </ul>
 */
public final class SimpleAuthPlugin implements MonacoPlugin {

    private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
            "monaco.auth.simple",
            "Simple Authentication",
            "1.0.0",
            ApiVersion.CURRENT,
            Set.of(PluginCapability.AUTHENTICATION),
            List.of()
    );

    private SimpleAuthProvider provider;

    @Override
    public PluginDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<PluginHook> hooks() {
        if (provider == null) {
            throw new IllegalStateException("Plugin not started");
        }
        return List.of(provider);
    }

    @Override
    public Mono<Void> start(PluginContext context) {
        return Mono.fromRunnable(() -> {
            PluginConfig config = context.config();

            String providerType = config.get("credential.provider").orElse("env");
            CredentialProvider credentialProvider = loadCredentialProvider(providerType);

            String encoderType = config.get("password.encoder").orElse("plain");
            PasswordEncoder encoder = createEncoder(encoderType);

            this.provider = new SimpleAuthProvider(credentialProvider, encoder);
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> this.provider = null);
    }

    private static CredentialProvider loadCredentialProvider(String type) {
        ServiceLoader<CredentialProvider> loader = ServiceLoader.load(CredentialProvider.class);
        for (CredentialProvider candidate : loader) {
            if (candidate.type().equals(type)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
                "No CredentialProvider found for type: " + type
                        + ". Ensure the provider module is on the classpath.");
    }

    private static PasswordEncoder createEncoder(String type) {
        return switch (type) {
            case "plain" -> new PlainPasswordEncoder();
            case "sha256" -> new Sha256PasswordEncoder();
            default -> throw new IllegalArgumentException("Unknown password encoder: " + type);
        };
    }
}

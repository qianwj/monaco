package cn.elvis.monaco.plugin.runtime.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Validated limits and deployments for the local plugin runtime. */
public record PluginRuntimeConfig(
        Path pluginsDirectory,
        Map<String, PluginDeployment> deployments,
        int maxPlugins,
        int maxJarsPerPlugin,
        long maxJarBytes,
        long maxManifestBytes,
        int maxManifestDepth,
        int maxYamlAliases,
        Duration lifecycleTimeout,
        Duration chainTimeout
) {

    public PluginRuntimeConfig {
        if (pluginsDirectory == null) {
            throw new IllegalArgumentException("Plugins directory must not be null");
        }
        pluginsDirectory = pluginsDirectory.toAbsolutePath().normalize();
        deployments = deployments == null ? Map.of() : Map.copyOf(deployments);
        if (maxPlugins < 0) {
            throw new IllegalArgumentException("Maximum plugin count must not be negative");
        }
        if (maxJarsPerPlugin < 1 || maxJarBytes < 1 || maxManifestBytes < 1) {
            throw new IllegalArgumentException("Plugin file limits must be positive");
        }
        if (maxManifestDepth < 1 || maxYamlAliases < 0) {
            throw new IllegalArgumentException("Plugin manifest parser limits are invalid");
        }
        requirePositive(lifecycleTimeout, "Lifecycle timeout");
        requirePositive(chainTimeout, "Chain timeout");
        deployments.forEach((id, deployment) -> {
            if (id == null || id.isBlank() || deployment == null) {
                throw new IllegalArgumentException("Plugin deployment entries must be complete");
            }
        });
    }

    public static PluginRuntimeConfig defaults(Path pluginsDirectory) {
        return new PluginRuntimeConfig(
                pluginsDirectory,
                Map.of(),
                128,
                32,
                128L * 1024 * 1024,
                256L * 1024,
                32,
                16,
                Duration.ofSeconds(10),
                Duration.ofMillis(500));
    }

    public PluginDeployment deploymentFor(String pluginId) {
        return deployments.getOrDefault(pluginId, PluginDeployment.defaults());
    }

    private static void requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}

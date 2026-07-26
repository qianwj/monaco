package cn.elvis.monaco.plugin.runtime.config;

import cn.elvis.monaco.plugin.api.support.PluginConfig;
import cn.elvis.monaco.plugin.runtime.event.EventDropPolicy;

import java.time.Duration;
import java.util.Map;

/** Broker-owned deployment policy for one plugin. */
public record PluginDeployment(
        boolean enabled,
        boolean required,
        int priority,
        Duration decisionTimeout,
        Duration eventTimeout,
        int maxConcurrency,
        int maxQueueSize,
        int eventQueueSize,
        EventDropPolicy eventDropPolicy,
        Map<String, String> config
) {

    private static final Duration DEFAULT_DECISION_TIMEOUT = Duration.ofMillis(100);
    private static final Duration DEFAULT_EVENT_TIMEOUT = Duration.ofMillis(500);

    public PluginDeployment {
        requirePositive(decisionTimeout, "Decision timeout");
        requirePositive(eventTimeout, "Event timeout");
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("Plugin max concurrency must be positive");
        }
        if (maxQueueSize < 0) {
            throw new IllegalArgumentException("Plugin max queue size must not be negative");
        }
        if (eventQueueSize < 1) {
            throw new IllegalArgumentException("Plugin event queue size must be positive");
        }
        if (eventDropPolicy == null) {
            throw new IllegalArgumentException("Plugin event drop policy must not be null");
        }
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public static PluginDeployment defaults() {
        return new PluginDeployment(
                true,
                false,
                100,
                DEFAULT_DECISION_TIMEOUT,
                DEFAULT_EVENT_TIMEOUT,
                8,
                256,
                1_024,
                EventDropPolicy.DROP_LATEST,
                Map.of());
    }

    public PluginConfig pluginConfig() {
        return PluginConfig.of(config);
    }

    private static void requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}

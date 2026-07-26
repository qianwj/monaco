package cn.elvis.monaco.plugin.api.lifecycle;

import cn.elvis.monaco.plugin.api.support.PluginClock;
import cn.elvis.monaco.plugin.api.support.PluginConfig;
import cn.elvis.monaco.plugin.api.support.PluginLogger;
import cn.elvis.monaco.plugin.api.support.PluginMetrics;
import cn.elvis.monaco.plugin.api.support.PluginScheduler;

/** Runtime-owned services that a plugin may use during its lifecycle. */
public record PluginContext(
        PluginConfig config,
        PluginLogger logger,
        PluginMetrics metrics,
        PluginClock clock,
        PluginScheduler scheduler,
        BrokerInfo broker
) {

    public PluginContext {
        if (config == null || logger == null || metrics == null || clock == null
                || scheduler == null || broker == null) {
            throw new IllegalArgumentException("Plugin context components must not be null");
        }
    }
}

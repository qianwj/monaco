package cn.elvis.monaco.plugin.runtime.lifecycle;

import cn.elvis.monaco.plugin.api.lifecycle.BrokerInfo;
import cn.elvis.monaco.plugin.api.lifecycle.PluginContext;
import cn.elvis.monaco.plugin.api.support.PluginClock;
import cn.elvis.monaco.plugin.api.support.PluginMetrics;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.support.RuntimePluginLogger;
import cn.elvis.monaco.plugin.runtime.support.RuntimePluginScheduler;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Default in-process context without implementation-specific metrics leakage. */
public final class DefaultPluginContextProvider implements PluginContextProvider {

    private final BrokerInfo brokerInfo;
    private final Set<String> created = ConcurrentHashMap.newKeySet();

    public DefaultPluginContextProvider(BrokerInfo brokerInfo) {
        if (brokerInfo == null) {
            throw new IllegalArgumentException("Broker info must not be null");
        }
        this.brokerInfo = brokerInfo;
    }

    @Override
    public Mono<PluginContext> contextFor(PluginHandle handle) {
        return Mono.fromSupplier(() -> {
            String pluginId = handle.descriptor().id();
            if (!created.add(pluginId)) {
                throw new IllegalStateException("Plugin context was requested more than once: " + pluginId);
            }
            RuntimePluginScheduler scheduler = new RuntimePluginScheduler(
                    pluginId, handle.executionClassLoader());
            handle.registerResource(scheduler);
            return new PluginContext(
                    handle.deployment().pluginConfig(),
                    new RuntimePluginLogger(pluginId),
                    PluginMetrics.noop(),
                    PluginClock.systemUtc(),
                    scheduler,
                    brokerInfo);
        });
    }
}

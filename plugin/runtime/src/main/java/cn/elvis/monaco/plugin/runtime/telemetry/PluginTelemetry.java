package cn.elvis.monaco.plugin.runtime.telemetry;

import cn.elvis.monaco.plugin.runtime.catalog.PluginState;

import java.time.Duration;

/** Internal telemetry sink; implementations must not throw into plugin flows. */
public interface PluginTelemetry {

    void stateChanged(String pluginId, PluginState previous, PluginState current);

    void invocationSucceeded(String pluginId, String hookId, Duration elapsed);

    void invocationFailed(String pluginId, String hookId, Throwable failure, Duration elapsed);

    void eventDropped(String pluginId);

    static PluginTelemetry noop() {
        return new PluginTelemetry() {
            @Override
            public void stateChanged(String pluginId, PluginState previous, PluginState current) { }

            @Override
            public void invocationSucceeded(String pluginId, String hookId, Duration elapsed) { }

            @Override
            public void invocationFailed(
                    String pluginId, String hookId, Throwable failure, Duration elapsed) { }

            @Override
            public void eventDropped(String pluginId) { }
        };
    }
}

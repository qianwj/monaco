package cn.elvis.monaco.plugin.api.support;

import java.time.Duration;
import java.util.Map;

/** Metrics facade that namespaces all instruments to the owning plugin. */
public interface PluginMetrics {

    void incrementCounter(String name, Map<String, String> tags);

    void recordDuration(String name, Duration duration, Map<String, String> tags);

    void recordGauge(String name, double value, Map<String, String> tags);

    static PluginMetrics noop() {
        return new PluginMetrics() {
            @Override
            public void incrementCounter(String name, Map<String, String> tags) { }

            @Override
            public void recordDuration(String name, Duration duration, Map<String, String> tags) { }

            @Override
            public void recordGauge(String name, double value, Map<String, String> tags) { }
        };
    }
}

package cn.elvis.monaco.plugin.api.support;

import java.time.Instant;

/** Read-only clock supplied by the plugin runtime. */
@FunctionalInterface
public interface PluginClock {

    Instant now();

    static PluginClock systemUtc() {
        return Instant::now;
    }
}

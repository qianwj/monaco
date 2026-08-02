package cn.elvis.monaco.plugin.runtime.telemetry;

import cn.elvis.monaco.plugin.runtime.catalog.PluginState;

import java.time.Duration;

/** Immutable health snapshot for one plugin. */
public record PluginHealth(
        String pluginId,
        PluginState state,
        long successfulInvocations,
        long failedInvocations,
        long droppedEvents,
        Duration totalInvocationTime
) { }

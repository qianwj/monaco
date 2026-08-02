package cn.elvis.monaco.plugin.runtime;

/** Broker-level state of the plugin subsystem. */
public enum PluginRuntimeState {
    NEW,
    STARTING,
    ACTIVE,
    STOPPING,
    STOPPED,
    FAILED
}

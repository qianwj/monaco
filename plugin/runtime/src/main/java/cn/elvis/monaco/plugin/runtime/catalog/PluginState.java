package cn.elvis.monaco.plugin.runtime.catalog;

/** Lifecycle state of a discovered plugin. */
public enum PluginState {
    DISCOVERED,
    VALIDATED,
    LOADED,
    STARTING,
    ACTIVE,
    DEGRADED,
    STOPPING,
    STOPPED,
    FAILED
}

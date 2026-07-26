package cn.elvis.monaco.plugin.api.hook;

/** Marker for a statically declared plugin extension point. */
public interface PluginHook {

    /** Identifier unique within the owning plugin. */
    String hookId();
}

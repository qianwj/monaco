package cn.elvis.monaco.plugin.api.lifecycle;

/** Service provider entry point for a trusted in-process plugin. */
@FunctionalInterface
public interface MonacoPluginFactory {

    MonacoPlugin create();
}

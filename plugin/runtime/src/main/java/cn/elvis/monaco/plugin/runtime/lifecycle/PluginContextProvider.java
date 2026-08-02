package cn.elvis.monaco.plugin.runtime.lifecycle;

import cn.elvis.monaco.plugin.api.lifecycle.PluginContext;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import reactor.core.publisher.Mono;

/** Creates the runtime-owned context for a plugin lifecycle. */
@FunctionalInterface
public interface PluginContextProvider {

    Mono<PluginContext> contextFor(PluginHandle handle);
}

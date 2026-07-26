package cn.elvis.monaco.plugin.api.lifecycle;

import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import reactor.core.publisher.Mono;

import java.util.List;

/** Lifecycle and immutable hook declaration for a Monaco plugin. */
public interface MonacoPlugin {

    PluginDescriptor descriptor();

    List<PluginHook> hooks();

    Mono<Void> start(PluginContext context);

    Mono<Void> stop();
}

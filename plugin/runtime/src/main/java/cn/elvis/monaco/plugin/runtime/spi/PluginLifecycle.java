package cn.elvis.monaco.plugin.runtime.spi;

import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.api.lifecycle.PluginContext;
import reactor.core.publisher.Mono;

/** Source-neutral lifecycle used by local and future remote plugins. */
public interface PluginLifecycle {

    Mono<Void> start(PluginContext context);

    Mono<Void> stop();

    static PluginLifecycle from(MonacoPlugin plugin) {
        return new PluginLifecycle() {
            @Override
            public Mono<Void> start(PluginContext context) {
                return Mono.defer(() -> plugin.start(context));
            }

            @Override
            public Mono<Void> stop() {
                return Mono.defer(plugin::stop);
            }
        };
    }
}

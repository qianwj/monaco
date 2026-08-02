package cn.elvis.monaco.plugin.runtime.spi;

import reactor.core.publisher.Mono;

/** Source-owned resource released when its plugin generation is discarded. */
@FunctionalInterface
public interface PluginResource {

    Mono<Void> close();

    static PluginResource none() {
        return Mono::empty;
    }

    static PluginResource from(AutoCloseable resource) {
        if (resource == null) {
            return none();
        }
        return () -> Mono.fromRunnable(() -> {
            try {
                resource.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot close plugin source resource", exception);
            }
        });
    }
}

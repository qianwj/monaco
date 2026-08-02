package cn.elvis.monaco.plugin.runtime.spi;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Discovers and validates plugins without starting their lifecycle. */
public interface PluginSource {

    Flux<PluginCandidate> load();

    default Mono<Void> close() {
        return Mono.empty();
    }
}

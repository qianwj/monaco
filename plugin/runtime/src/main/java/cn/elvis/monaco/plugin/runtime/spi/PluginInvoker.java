package cn.elvis.monaco.plugin.runtime.spi;

import reactor.core.publisher.Mono;

import java.time.Duration;

/** Executes calls under source-specific isolation and deadlines. */
public interface PluginInvoker {

    <R> Mono<R> invoke(HookInvocation<R> invocation);

    Mono<Void> drain(Duration timeout);

    Mono<Void> close();
}

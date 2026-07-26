package cn.elvis.monaco.runtime.dispatch;

import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface CommandDispatcher {
    <R> Mono<R> dispatch(String clientId, Function<String, Mono<R>> task);
}

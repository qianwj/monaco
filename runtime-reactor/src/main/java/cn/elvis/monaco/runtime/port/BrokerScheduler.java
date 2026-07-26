package cn.elvis.monaco.runtime.port;

import reactor.core.publisher.Mono;

import java.time.Duration;

public interface BrokerScheduler {
    Mono<Void> schedule(String key, Duration delay, Runnable task);
    Mono<Void> cancel(String key);
}

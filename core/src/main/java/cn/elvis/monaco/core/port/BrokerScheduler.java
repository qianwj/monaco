package cn.elvis.monaco.core.port;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Timer scheduling port for keep-alive, session expiry, and will delay.
 */
public interface BrokerScheduler {
    Mono<Void> schedule(String key, Duration delay, Runnable task);
    Mono<Void> cancel(String key);
}

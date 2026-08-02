package cn.elvis.monaco.plugin.runtime.spi;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

/** Lazy operation submitted to a plugin-specific invoker. */
public record HookInvocation<R>(
        String hookId,
        String operation,
        Duration timeout,
        Supplier<? extends Mono<? extends R>> action
) {

    public HookInvocation {
        if (hookId == null || hookId.isBlank()) {
            throw new IllegalArgumentException("Hook id must not be blank");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Hook operation must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Hook timeout must be positive");
        }
        if (action == null) {
            throw new IllegalArgumentException("Hook action must not be null");
        }
    }
}

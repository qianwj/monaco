package cn.elvis.monaco.plugin.runtime.invoke;

import cn.elvis.monaco.plugin.runtime.config.PluginDeployment;
import cn.elvis.monaco.plugin.runtime.event.EventDropPolicy;
import cn.elvis.monaco.plugin.runtime.spi.HookInvocation;
import cn.elvis.monaco.plugin.runtime.telemetry.InMemoryPluginTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LocalPluginInvokerTest {

    private LocalPluginInvoker invoker;

    @AfterEach
    void closeInvoker() {
        if (invoker != null) {
            invoker.close().block();
        }
    }

    @Test
    void isLazyAndEnforcesConcurrencyAndQueueLimits() {
        invoker = new LocalPluginInvoker("bounded", deployment(1, 1), new InMemoryPluginTelemetry());
        AtomicInteger calls = new AtomicInteger();
        Sinks.Empty<Void> gate = Sinks.empty();

        Mono<String> first = invoker.invoke(invocation(
                "first", () -> gate.asMono().thenReturn("first"), calls));
        assertEquals(0, calls.get());
        Disposable firstSubscription = first.subscribe();

        Mono<String> second = invoker.invoke(invocation(
                "second", () -> Mono.just("second"), calls));
        var secondFuture = second.toFuture();

        StepVerifier.create(invoker.invoke(invocation(
                        "third", () -> Mono.just("third"), calls)))
                .expectErrorSatisfies(failure -> assertEquals(
                        PluginInvocationException.Kind.QUEUE_FULL,
                        ((PluginInvocationException) failure).kind()))
                .verify();

        gate.tryEmitEmpty();
        assertEquals("second", secondFuture.join());
        assertEquals(2, calls.get());
        firstSubscription.dispose();
    }

    @Test
    void normalizesTimeoutWithoutRetrying() {
        invoker = new LocalPluginInvoker("timeout", deployment(1, 0), new InMemoryPluginTelemetry());
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(invoker.invoke(new HookInvocation<>(
                        "slow",
                        "test",
                        Duration.ofMillis(25),
                        () -> {
                            calls.incrementAndGet();
                            return Mono.never();
                        })))
                .expectErrorSatisfies(failure -> assertEquals(
                        PluginInvocationException.Kind.TIMEOUT,
                        ((PluginInvocationException) failure).kind()))
                .verify();

        assertEquals(1, calls.get());
    }

    @Test
    void drainWaitsForInFlightWorkAndRejectsNewCalls() {
        invoker = new LocalPluginInvoker("drain", deployment(1, 1), new InMemoryPluginTelemetry());
        Sinks.Empty<Void> gate = Sinks.empty();
        Disposable active = invoker.invoke(new HookInvocation<>(
                        "active", "test", Duration.ofSeconds(1), gate::asMono))
                .subscribe();

        var drained = invoker.drain(Duration.ofSeconds(1)).toFuture();
        StepVerifier.create(invoker.invoke(new HookInvocation<>(
                        "late", "test", Duration.ofSeconds(1), Mono::empty)))
                .expectErrorSatisfies(failure -> assertEquals(
                        PluginInvocationException.Kind.CLOSED,
                        ((PluginInvocationException) failure).kind()))
                .verify();
        gate.tryEmitEmpty();
        drained.join();
        active.dispose();
    }

    @Test
    void installsPluginContextClassLoaderOnOwnedThreads() {
        ClassLoader pluginLoader = new ClassLoader() { };
        invoker = new LocalPluginInvoker(
                "context-loader", deployment(1, 0), new InMemoryPluginTelemetry(), pluginLoader);

        ClassLoader observed = invoker.invoke(new HookInvocation<>(
                        "loader",
                        "test",
                        Duration.ofSeconds(1),
                        () -> Mono.fromSupplier(() -> Thread.currentThread().getContextClassLoader())))
                .block();

        assertSame(pluginLoader, observed);
    }

    private static HookInvocation<String> invocation(
            String id,
            java.util.function.Supplier<Mono<String>> action,
            AtomicInteger calls
    ) {
        return new HookInvocation<>(id, "test", Duration.ofSeconds(1), () -> {
            calls.incrementAndGet();
            return action.get();
        });
    }

    private static PluginDeployment deployment(int concurrency, int queue) {
        return new PluginDeployment(
                true,
                false,
                100,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                concurrency,
                queue,
                4,
                EventDropPolicy.DROP_LATEST,
                Map.of());
    }
}

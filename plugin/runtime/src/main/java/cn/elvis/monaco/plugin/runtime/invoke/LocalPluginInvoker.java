package cn.elvis.monaco.plugin.runtime.invoke;

import cn.elvis.monaco.plugin.runtime.config.PluginDeployment;
import cn.elvis.monaco.plugin.runtime.spi.HookInvocation;
import cn.elvis.monaco.plugin.runtime.spi.PluginInvoker;
import cn.elvis.monaco.plugin.runtime.support.PluginThreadFactory;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-plugin bounded scheduler, queue, deadline, and circuit breaker. */
public final class LocalPluginInvoker implements PluginInvoker {

    private final String pluginId;
    private final int maxConcurrency;
    private final int maxQueueSize;
    private final PluginTelemetry telemetry;
    private final Scheduler scheduler;
    private final CircuitBreaker circuitBreaker;
    private final Object lock = new Object();
    private final ArrayDeque<Pending<?>> waiting = new ArrayDeque<>();
    private final Set<Pending<?>> running = new HashSet<>();
    private final Sinks.Empty<Void> drained = Sinks.empty();
    private boolean accepting = true;
    private boolean closed;

    public LocalPluginInvoker(
            String pluginId,
            PluginDeployment deployment,
            PluginTelemetry telemetry
    ) {
        this(pluginId, deployment, telemetry, LocalPluginInvoker.class.getClassLoader());
    }

    public LocalPluginInvoker(
            String pluginId,
            PluginDeployment deployment,
            PluginTelemetry telemetry,
            ClassLoader executionClassLoader
    ) {
        if (pluginId == null || pluginId.isBlank() || deployment == null || telemetry == null) {
            throw new IllegalArgumentException("Local plugin invoker components must not be null");
        }
        this.pluginId = pluginId;
        this.maxConcurrency = deployment.maxConcurrency();
        this.maxQueueSize = deployment.maxQueueSize();
        this.telemetry = telemetry;
        scheduler = Schedulers.newBoundedElastic(
                maxConcurrency,
                Math.max(1, maxQueueSize),
                new PluginThreadFactory("monaco-plugin-" + pluginId, executionClassLoader),
                60);
        circuitBreaker = CircuitBreaker.of(
                pluginId,
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(5)
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build());
    }

    @Override
    public <R> Mono<R> invoke(HookInvocation<R> invocation) {
        return Mono.defer(() -> {
            Pending<R> pending = new Pending<>(invocation);
            boolean startNow;
            synchronized (lock) {
                if (closed || !accepting) {
                    return Mono.error(closedFailure());
                }
                if (running.size() < maxConcurrency) {
                    running.add(pending);
                    pending.running = true;
                    startNow = true;
                } else if (waiting.size() < maxQueueSize) {
                    waiting.addLast(pending);
                    startNow = false;
                } else {
                    return Mono.error(new PluginInvocationException(
                            PluginInvocationException.Kind.QUEUE_FULL,
                            "Plugin invocation queue is full: " + pluginId));
                }
            }
            if (startNow) {
                start(pending);
            }
            return pending.result.asMono().doOnCancel(() -> cancel(pending));
        });
    }

    @Override
    public Mono<Void> drain(Duration timeout) {
        return Mono.defer(() -> {
            synchronized (lock) {
                accepting = false;
                if (running.isEmpty() && waiting.isEmpty()) {
                    drained.tryEmitEmpty();
                }
            }
            return drained.asMono().timeout(timeout);
        });
    }

    @Override
    public Mono<Void> close() {
        return Mono.fromRunnable(() -> {
            List<Pending<?>> queued;
            List<Pending<?>> active;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                accepting = false;
                queued = new ArrayList<>(waiting);
                active = new ArrayList<>(running);
                waiting.clear();
                if (active.isEmpty()) {
                    drained.tryEmitEmpty();
                }
            }
            PluginInvocationException failure = closedFailure();
            queued.forEach(pending -> pending.result.tryEmitError(failure));
            active.forEach(pending -> {
                pending.result.tryEmitError(failure);
                Disposable subscription = pending.subscription;
                if (subscription != null) {
                    subscription.dispose();
                }
            });
            scheduler.dispose();
            drained.tryEmitEmpty();
        });
    }

    private <R> void start(Pending<R> pending) {
        long startedAt = System.nanoTime();
        Mono<R> guarded = Mono.defer(() -> {
                    Mono<? extends R> operation = pending.invocation.action().get();
                    if (operation == null) {
                        return Mono.error(new IllegalStateException("Plugin hook returned null Mono"));
                    }
                    return operation.<R>map(value -> value);
                })
                .subscribeOn(scheduler)
                .timeout(pending.invocation.timeout())
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(this::normalizeFailure)
                .doOnSuccess(ignored -> telemetry.invocationSucceeded(
                        pluginId,
                        pending.invocation.hookId(),
                        elapsed(startedAt)))
                .doOnError(failure -> telemetry.invocationFailed(
                        pluginId,
                        pending.invocation.hookId(),
                        failure,
                        elapsed(startedAt)))
                .doFinally(ignored -> finish(pending));

        Disposable subscription = guarded.subscribe(
                value -> pending.result.tryEmitValue(value),
                pending.result::tryEmitError,
                pending.result::tryEmitEmpty);
        pending.subscription = subscription;
        if (pending.cancelled.get()) {
            subscription.dispose();
        }
    }

    private void cancel(Pending<?> pending) {
        Disposable subscription = null;
        synchronized (lock) {
            if (!pending.cancelled.compareAndSet(false, true)) {
                return;
            }
            if (!pending.running) {
                waiting.remove(pending);
                if (!accepting && running.isEmpty() && waiting.isEmpty()) {
                    drained.tryEmitEmpty();
                }
            } else {
                subscription = pending.subscription;
            }
        }
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private void finish(Pending<?> completed) {
        Pending<?> next = null;
        synchronized (lock) {
            if (!running.remove(completed)) {
                return;
            }
            completed.running = false;
            while (!closed && !waiting.isEmpty()) {
                Pending<?> candidate = waiting.removeFirst();
                if (!candidate.cancelled.get()) {
                    candidate.running = true;
                    running.add(candidate);
                    next = candidate;
                    break;
                }
            }
            if (!accepting && running.isEmpty() && waiting.isEmpty()) {
                drained.tryEmitEmpty();
            }
        }
        if (next != null) {
            startUnchecked(next);
        }
    }

    @SuppressWarnings("unchecked")
    private void startUnchecked(Pending<?> pending) {
        start((Pending<Object>) pending);
    }

    private Throwable normalizeFailure(Throwable failure) {
        if (failure instanceof PluginInvocationException) {
            return failure;
        }
        if (failure instanceof TimeoutException) {
            return new PluginInvocationException(
                    PluginInvocationException.Kind.TIMEOUT,
                    "Plugin invocation timed out: " + pluginId,
                    failure);
        }
        if (failure instanceof CallNotPermittedException) {
            return new PluginInvocationException(
                    PluginInvocationException.Kind.CIRCUIT_OPEN,
                    "Plugin circuit is open: " + pluginId,
                    failure);
        }
        if (failure instanceof RejectedExecutionException) {
            return new PluginInvocationException(
                    PluginInvocationException.Kind.QUEUE_FULL,
                    "Plugin scheduler rejected invocation: " + pluginId,
                    failure);
        }
        return new PluginInvocationException(
                PluginInvocationException.Kind.FAILURE,
                "Plugin invocation failed: " + pluginId,
                failure);
    }

    private PluginInvocationException closedFailure() {
        return new PluginInvocationException(
                PluginInvocationException.Kind.CLOSED,
                "Plugin invoker is closed: " + pluginId);
    }

    private static Duration elapsed(long startedAt) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - startedAt));
    }

    private static final class Pending<R> {
        private final HookInvocation<R> invocation;
        private final Sinks.One<R> result = Sinks.one();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Disposable subscription;
        private boolean running;

        private Pending(HookInvocation<R> invocation) {
            this.invocation = invocation;
        }
    }
}

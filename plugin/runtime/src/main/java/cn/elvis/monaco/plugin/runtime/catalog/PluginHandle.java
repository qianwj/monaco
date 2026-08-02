package cn.elvis.monaco.plugin.runtime.catalog;

import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.api.lifecycle.PluginContext;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.config.PluginDeployment;
import cn.elvis.monaco.plugin.runtime.spi.PluginInvoker;
import cn.elvis.monaco.plugin.runtime.spi.PluginLifecycle;
import cn.elvis.monaco.plugin.runtime.spi.PluginCandidate;
import cn.elvis.monaco.plugin.runtime.spi.PluginResource;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Loaded source-neutral plugin and its owned resources. */
public final class PluginHandle {

    private final PluginDescriptor descriptor;
    private final List<PluginHook> hooks;
    private final PluginLifecycle lifecycle;
    private final PluginInvoker invoker;
    private final PluginFingerprint fingerprint;
    private final PluginDeployment deployment;
    private final PluginTelemetry telemetry;
    private final ClassLoader executionClassLoader;
    private final List<PluginResource> resources = new ArrayList<>();
    private final AtomicReference<PluginState> state = new AtomicReference<>(PluginState.LOADED);
    private final AtomicBoolean closed = new AtomicBoolean();

    public PluginHandle(
            PluginDescriptor descriptor,
            List<PluginHook> hooks,
            PluginLifecycle lifecycle,
            PluginInvoker invoker,
            PluginFingerprint fingerprint,
            PluginDeployment deployment,
            PluginTelemetry telemetry,
            AutoCloseable resource
    ) {
        this(
                descriptor,
                hooks,
                lifecycle,
                invoker,
                fingerprint,
                deployment,
                telemetry,
                PluginHandle.class.getClassLoader(),
                PluginResource.from(resource));
    }

    public PluginHandle(
            PluginCandidate candidate,
            PluginDeployment deployment,
            PluginTelemetry telemetry
    ) {
        this(
                candidate.descriptor(),
                candidate.hooks(),
                candidate.lifecycle(),
                candidate.invoker(),
                new PluginFingerprint(candidate.fingerprint()),
                deployment,
                telemetry,
                candidate.executionClassLoader(),
                candidate.resource());
    }

    private PluginHandle(
            PluginDescriptor descriptor,
            List<PluginHook> hooks,
            PluginLifecycle lifecycle,
            PluginInvoker invoker,
            PluginFingerprint fingerprint,
            PluginDeployment deployment,
            PluginTelemetry telemetry,
            ClassLoader executionClassLoader,
            PluginResource resource
    ) {
        if (descriptor == null || lifecycle == null || invoker == null || fingerprint == null
                || deployment == null || telemetry == null) {
            throw new IllegalArgumentException("Plugin handle components must not be null");
        }
        this.descriptor = descriptor;
        this.hooks = hooks == null ? List.of() : List.copyOf(hooks);
        this.lifecycle = lifecycle;
        this.invoker = invoker;
        this.fingerprint = fingerprint;
        this.deployment = deployment;
        this.telemetry = telemetry;
        this.executionClassLoader = executionClassLoader;
        resources.add(resource);
        telemetry.stateChanged(descriptor.id(), PluginState.VALIDATED, PluginState.LOADED);
    }

    public PluginDescriptor descriptor() {
        return descriptor;
    }

    public List<PluginHook> hooks() {
        return hooks;
    }

    public PluginInvoker invoker() {
        return invoker;
    }

    public PluginFingerprint fingerprint() {
        return fingerprint;
    }

    public PluginDeployment deployment() {
        return deployment;
    }

    public PluginState state() {
        return state.get();
    }

    public ClassLoader executionClassLoader() {
        return executionClassLoader;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public Mono<Void> start(PluginContext context) {
        return Mono.defer(() -> {
            transition(PluginState.STARTING);
            return lifecycle.start(context)
                    .doOnSuccess(ignored -> transition(PluginState.ACTIVE))
                    .doOnError(ignored -> transition(PluginState.FAILED));
        });
    }

    public Mono<Void> stop() {
        return Mono.defer(() -> {
            PluginState current = state.get();
            if (current == PluginState.STOPPED) {
                return Mono.empty();
            }
            transition(PluginState.STOPPING);
            return lifecycle.stop()
                    .doOnSuccess(ignored -> transition(PluginState.STOPPED))
                    .doOnError(ignored -> transition(PluginState.FAILED));
        });
    }

    public void markDegraded() {
        transition(PluginState.DEGRADED);
    }

    public synchronized void registerResource(AutoCloseable resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Plugin resource must not be null");
        }
        if (closed.get()) {
            throw new IllegalStateException("Plugin handle is already closed");
        }
        resources.add(PluginResource.from(resource));
    }

    public Mono<Void> close() {
        return Mono.defer(() -> {
            if (!closed.compareAndSet(false, true)) {
                return Mono.empty();
            }
            return invoker.close().onErrorResume(ignored -> Mono.empty())
                    .thenMany(Flux.fromIterable(reverseResources())
                            .concatMap(resource -> resource.close().onErrorResume(ignored -> Mono.empty())))
                    .then();
        });
    }

    private synchronized List<PluginResource> reverseResources() {
        List<PluginResource> reversed = new ArrayList<>(resources);
        java.util.Collections.reverse(reversed);
        resources.clear();
        return reversed;
    }

    private void transition(PluginState next) {
        PluginState previous = state.getAndUpdate(current -> {
            if (!allowed(current, next)) {
                throw new PluginRuntimeException(
                        "Invalid plugin state transition for " + descriptor.id() + ": " + current + " -> " + next);
            }
            return next;
        });
        telemetry.stateChanged(descriptor.id(), previous, next);
    }

    private static boolean allowed(PluginState current, PluginState next) {
        return current == next || switch (current) {
            case LOADED -> next == PluginState.STARTING
                    || next == PluginState.DEGRADED
                    || next == PluginState.STOPPING;
            case STARTING -> next == PluginState.ACTIVE || next == PluginState.FAILED;
            case ACTIVE -> next == PluginState.DEGRADED || next == PluginState.STOPPING;
            case DEGRADED -> next == PluginState.STOPPING;
            case FAILED -> next == PluginState.DEGRADED || next == PluginState.STOPPING;
            case STOPPING -> next == PluginState.STOPPED || next == PluginState.FAILED;
            case STOPPED -> false;
            case DISCOVERED, VALIDATED -> next == PluginState.LOADED;
        };
    }

}

package cn.elvis.monaco.plugin.runtime.lifecycle;

import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.catalog.PluginState;
import cn.elvis.monaco.plugin.runtime.config.PluginRuntimeConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/** Starts in dependency order and performs deterministic rollback and stop. */
public final class LifecycleCoordinator {

    private final PluginRuntimeConfig config;
    private final DependencyGraph dependencyGraph;

    public LifecycleCoordinator(PluginRuntimeConfig config) {
        this(config, new DependencyGraph());
    }

    public LifecycleCoordinator(PluginRuntimeConfig config, DependencyGraph dependencyGraph) {
        if (config == null || dependencyGraph == null) {
            throw new IllegalArgumentException("Lifecycle coordinator components must not be null");
        }
        this.config = config;
        this.dependencyGraph = dependencyGraph;
    }

    public Mono<List<PluginHandle>> start(
            List<PluginHandle> handles,
            PluginContextProvider contextProvider
    ) {
        return Mono.defer(() -> {
            List<PluginHandle> ordered = dependencyGraph.order(handles);
            List<PluginHandle> started = new ArrayList<>();
            return Flux.fromIterable(ordered)
                    .concatMap(handle -> ensureDependenciesActive(handle, ordered)
                            .then(contextProvider.contextFor(handle))
                            .flatMap(handle::start)
                            .timeout(config.lifecycleTimeout())
                            .doOnSuccess(ignored -> started.add(handle))
                            .onErrorResume(failure -> onStartFailure(
                                    handle, failure, ordered, started)))
                    .then(Mono.fromSupplier(() -> ordered));
        });
    }

    public Mono<Void> stop(List<PluginHandle> ordered) {
        List<PluginHandle> reversed = new ArrayList<>(ordered);
        java.util.Collections.reverse(reversed);
        return Flux.fromIterable(reversed)
                .concatMap(handle -> stopHandle(handle).then(handle.close()))
                .then();
    }

    public Mono<Void> drain(List<PluginHandle> handles) {
        return Flux.fromIterable(handles)
                .flatMap(handle -> handle.invoker().drain(config.chainTimeout())
                        .onErrorResume(ignored -> Mono.empty()))
                .then();
    }

    private Mono<Void> onStartFailure(
            PluginHandle failed,
            Throwable failure,
            List<PluginHandle> ordered,
            List<PluginHandle> started
    ) {
        if (!failed.deployment().required()) {
            failed.markDegraded();
            return failed.close();
        }
        return rollback(ordered, started)
                .then(Mono.error(new PluginRuntimeException(
                        "Required plugin failed to start: " + failed.descriptor().id(), failure)));
    }

    private Mono<Void> rollback(List<PluginHandle> ordered, List<PluginHandle> started) {
        List<PluginHandle> reversedStarted = new ArrayList<>(started);
        java.util.Collections.reverse(reversedStarted);
        return Flux.fromIterable(reversedStarted)
                .concatMap(handle -> stopHandle(handle).then(handle.close()))
                .thenMany(Flux.fromIterable(ordered)
                        .filter(handle -> !started.contains(handle))
                        .concatMap(PluginHandle::close))
                .then();
    }

    private Mono<Void> stopHandle(PluginHandle handle) {
        if (handle.isClosed()
                || handle.state() == PluginState.LOADED
                || handle.state() == PluginState.STOPPED) {
            return Mono.empty();
        }
        return handle.stop()
                .timeout(config.lifecycleTimeout())
                .onErrorResume(ignored -> Mono.empty());
    }

    private static Mono<Void> ensureDependenciesActive(
            PluginHandle handle,
            List<PluginHandle> ordered
    ) {
        for (var dependency : handle.descriptor().dependencies()) {
            PluginHandle required = ordered.stream()
                    .filter(candidate -> candidate.descriptor().id().equals(dependency.pluginId()))
                    .findFirst()
                    .orElseThrow();
            if (required.state() != PluginState.ACTIVE) {
                return Mono.error(new PluginRuntimeException(
                        "Plugin dependency is not active for " + handle.descriptor().id()
                                + ": " + dependency.pluginId()));
            }
        }
        return Mono.empty();
    }
}

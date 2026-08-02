package cn.elvis.monaco.plugin.runtime.lifecycle;

import cn.elvis.monaco.plugin.api.descriptor.PluginDependency;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.catalog.PluginState;
import cn.elvis.monaco.plugin.runtime.config.PluginRuntimeConfig;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import cn.elvis.monaco.plugin.runtime.testsupport.PluginTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifecycleCoordinatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void requiredFailureRollsBackStartedPlugins() {
        List<String> calls = new ArrayList<>();
        PluginHandle first = handle(
                "first", false, List.of(),
                Mono.fromRunnable(() -> calls.add("start:first")),
                Mono.fromRunnable(() -> calls.add("stop:first")));
        PluginHandle failed = handle(
                "failed", true, List.of(new PluginDependency("first")),
                Mono.error(new IllegalStateException("boom")),
                Mono.fromRunnable(() -> calls.add("stop:failed")));

        StepVerifier.create(coordinator().start(
                        List.of(failed, first), ignored -> Mono.just(PluginTestFixtures.context())))
                .expectError(PluginRuntimeException.class)
                .verify();

        assertEquals(List.of("start:first", "stop:first"), calls);
        assertEquals(PluginState.STOPPED, first.state());
        assertEquals(PluginState.FAILED, failed.state());
    }

    @Test
    void optionalFailureDegradesDependentsWithoutStartingThem() {
        List<String> calls = new ArrayList<>();
        PluginHandle failed = handle(
                "optional", false, List.of(),
                Mono.error(new IllegalStateException("boom")), Mono.empty());
        PluginHandle dependent = handle(
                "dependent", false, List.of(new PluginDependency("optional")),
                Mono.fromRunnable(() -> calls.add("unexpected")), Mono.empty());

        StepVerifier.create(coordinator().start(
                        List.of(dependent, failed), ignored -> Mono.just(PluginTestFixtures.context())))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(List.of(), calls);
        assertEquals(PluginState.DEGRADED, failed.state());
        assertEquals(PluginState.DEGRADED, dependent.state());
    }

    @Test
    void stopsInReverseDependencyOrder() {
        List<String> calls = new ArrayList<>();
        PluginHandle base = handle(
                "base", false, List.of(),
                Mono.fromRunnable(() -> calls.add("start:base")),
                Mono.fromRunnable(() -> calls.add("stop:base")));
        PluginHandle child = handle(
                "child", false, List.of(new PluginDependency("base")),
                Mono.fromRunnable(() -> calls.add("start:child")),
                Mono.fromRunnable(() -> calls.add("stop:child")));

        List<PluginHandle> ordered = coordinator()
                .start(List.of(child, base), ignored -> Mono.just(PluginTestFixtures.context())).block();
        coordinator().stop(ordered).block();

        assertEquals(
                List.of("start:base", "start:child", "stop:child", "stop:base"),
                calls);
    }

    private LifecycleCoordinator coordinator() {
        return new LifecycleCoordinator(PluginRuntimeConfig.defaults(temporaryDirectory));
    }

    private static PluginHandle handle(
            String id,
            boolean required,
            List<PluginDependency> dependencies,
            Mono<Void> start,
            Mono<Void> stop
    ) {
        return PluginTestFixtures.handle(
                id,
                "1.0.0",
                dependencies,
                PluginTestFixtures.deployment(required, 100),
                List.of(),
                PluginTestFixtures.lifecycle(start, stop),
                new PluginTestFixtures.DirectPluginInvoker(),
                PluginTelemetry.noop());
    }
}

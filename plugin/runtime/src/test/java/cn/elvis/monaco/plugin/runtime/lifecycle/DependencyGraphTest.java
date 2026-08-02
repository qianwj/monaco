package cn.elvis.monaco.plugin.runtime.lifecycle;

import cn.elvis.monaco.plugin.api.descriptor.PluginDependency;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import cn.elvis.monaco.plugin.runtime.testsupport.PluginTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyGraphTest {

    @Test
    void ordersDependenciesBeforePriorityAndId() {
        PluginHandle base = handle("base", "1.5.0", List.of(), 100);
        PluginHandle dependent = handle(
                "dependent", "1.0.0", List.of(new PluginDependency("base", "^1.0.0")), 1);
        PluginHandle independent = handle("alpha", "1.0.0", List.of(), 50);

        List<String> ordered = new DependencyGraph().order(List.of(dependent, base, independent)).stream()
                .map(handle -> handle.descriptor().id())
                .toList();

        assertEquals(List.of("alpha", "base", "dependent"), ordered);
    }

    @Test
    void rejectsMissingMismatchedAndCyclicDependencies() {
        DependencyGraph graph = new DependencyGraph();
        PluginHandle missing = handle(
                "consumer", "1.0.0", List.of(new PluginDependency("missing")), 1);
        assertThrows(PluginRuntimeException.class, () -> graph.order(List.of(missing)));

        PluginHandle base = handle("base", "2.0.0", List.of(), 1);
        PluginHandle mismatched = handle(
                "consumer", "1.0.0", List.of(new PluginDependency("base", "<2.0.0")), 1);
        assertThrows(PluginRuntimeException.class, () -> graph.order(List.of(base, mismatched)));

        PluginHandle left = handle("left", "1.0.0", List.of(new PluginDependency("right")), 1);
        PluginHandle right = handle("right", "1.0.0", List.of(new PluginDependency("left")), 1);
        assertThrows(PluginRuntimeException.class, () -> graph.order(List.of(left, right)));
    }

    private static PluginHandle handle(
            String id,
            String version,
            List<PluginDependency> dependencies,
            int priority
    ) {
        return PluginTestFixtures.handle(
                id,
                version,
                dependencies,
                PluginTestFixtures.deployment(false, priority),
                List.of(),
                PluginTestFixtures.lifecycle(reactor.core.publisher.Mono.empty(), reactor.core.publisher.Mono.empty()),
                new PluginTestFixtures.DirectPluginInvoker(),
                PluginTelemetry.noop());
    }
}

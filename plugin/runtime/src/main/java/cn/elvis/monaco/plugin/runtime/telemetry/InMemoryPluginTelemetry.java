package cn.elvis.monaco.plugin.runtime.telemetry;

import cn.elvis.monaco.plugin.runtime.catalog.PluginState;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe telemetry implementation intended for health and tests. */
public final class InMemoryPluginTelemetry implements PluginTelemetry {

    private final ConcurrentHashMap<String, MutableStats> stats = new ConcurrentHashMap<>();

    @Override
    public void stateChanged(String pluginId, PluginState previous, PluginState current) {
        stats(pluginId).state.set(current);
    }

    @Override
    public void invocationSucceeded(String pluginId, String hookId, Duration elapsed) {
        MutableStats plugin = stats(pluginId);
        plugin.successes.incrementAndGet();
        plugin.totalNanos.addAndGet(elapsed.toNanos());
    }

    @Override
    public void invocationFailed(String pluginId, String hookId, Throwable failure, Duration elapsed) {
        MutableStats plugin = stats(pluginId);
        plugin.failures.incrementAndGet();
        plugin.totalNanos.addAndGet(elapsed.toNanos());
    }

    @Override
    public void eventDropped(String pluginId) {
        stats(pluginId).droppedEvents.incrementAndGet();
    }

    public Map<String, PluginHealth> snapshot() {
        return stats.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().snapshot(entry.getKey())));
    }

    private MutableStats stats(String pluginId) {
        return stats.computeIfAbsent(pluginId, ignored -> new MutableStats());
    }

    private static final class MutableStats {
        private final AtomicReference<PluginState> state = new AtomicReference<>(PluginState.DISCOVERED);
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong droppedEvents = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();

        private PluginHealth snapshot(String pluginId) {
            return new PluginHealth(
                    pluginId,
                    state.get(),
                    successes.get(),
                    failures.get(),
                    droppedEvents.get(),
                    Duration.ofNanos(totalNanos.get()));
        }
    }
}

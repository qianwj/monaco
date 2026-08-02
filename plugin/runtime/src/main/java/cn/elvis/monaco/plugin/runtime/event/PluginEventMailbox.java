package cn.elvis.monaco.plugin.runtime.event;

import cn.elvis.monaco.plugin.api.event.PluginEvent;
import cn.elvis.monaco.plugin.api.hook.PluginEventListener;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.registry.HookBinding;
import cn.elvis.monaco.plugin.runtime.spi.HookInvocation;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.ArrayDeque;
import java.util.List;

/** Sequential bounded event queue owned by one plugin. */
final class PluginEventMailbox {

    private final PluginHandle plugin;
    private final List<HookBinding<PluginEventListener>> listeners;
    private final int capacity;
    private final EventDropPolicy dropPolicy;
    private final PluginTelemetry telemetry;
    private final ArrayDeque<PluginEvent> queue = new ArrayDeque<>();
    private final Sinks.Empty<Void> drained = Sinks.empty();
    private boolean active;
    private boolean closed;

    PluginEventMailbox(
            PluginHandle plugin,
            List<HookBinding<PluginEventListener>> listeners,
            PluginTelemetry telemetry
    ) {
        this.plugin = plugin;
        this.listeners = List.copyOf(listeners);
        this.capacity = plugin.deployment().eventQueueSize();
        this.dropPolicy = plugin.deployment().eventDropPolicy();
        this.telemetry = telemetry;
    }

    void offer(PluginEvent event) {
        PluginEvent start = null;
        synchronized (this) {
            if (closed) {
                telemetry.eventDropped(plugin.descriptor().id());
                return;
            }
            if (!active) {
                active = true;
                start = event;
            } else if (queue.size() < capacity) {
                queue.addLast(event);
            } else {
                telemetry.eventDropped(plugin.descriptor().id());
                if (dropPolicy == EventDropPolicy.DROP_OLDEST) {
                    queue.removeFirst();
                    queue.addLast(event);
                }
            }
        }
        if (start != null) {
            process(start);
        }
    }

    Mono<Void> close() {
        synchronized (this) {
            closed = true;
            queue.clear();
            if (!active) {
                drained.tryEmitEmpty();
            }
        }
        return drained.asMono();
    }

    private void process(PluginEvent event) {
        Flux.fromIterable(listeners)
                .concatMap(binding -> plugin.invoker().invoke(new HookInvocation<>(
                                binding.hook().hookId(),
                                "event-listener",
                                plugin.deployment().eventTimeout(),
                                () -> binding.hook().onEvent(event)))
                        .onErrorResume(ignored -> Mono.empty()))
                .then()
                .doFinally(ignored -> advance())
                .subscribe();
    }

    private void advance() {
        PluginEvent next = null;
        synchronized (this) {
            if (!closed && !queue.isEmpty()) {
                next = queue.removeFirst();
            } else {
                active = false;
                if (closed) {
                    drained.tryEmitEmpty();
                }
            }
        }
        if (next != null) {
            process(next);
        }
    }
}

package cn.elvis.monaco.plugin.runtime.event;

import cn.elvis.monaco.plugin.api.event.PluginEvent;
import cn.elvis.monaco.plugin.api.hook.PluginEventListener;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.registry.HookBinding;
import cn.elvis.monaco.plugin.runtime.registry.HookSnapshot;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Best-effort dispatcher with an independent ordered mailbox per plugin. */
public final class EventDispatcher {

    private final PluginTelemetry telemetry;
    private volatile List<PluginEventMailbox> mailboxes = List.of();

    public EventDispatcher(PluginTelemetry telemetry) {
        this.telemetry = java.util.Objects.requireNonNull(telemetry, "telemetry");
    }

    public synchronized void publish(HookSnapshot snapshot) {
        if (!mailboxes.isEmpty()) {
            throw new IllegalStateException("Event dispatcher snapshot is already published");
        }
        Map<PluginHandle, List<HookBinding<PluginEventListener>>> grouped = new LinkedHashMap<>();
        snapshot.eventListeners().forEach(binding -> grouped
                .computeIfAbsent(binding.plugin(), ignored -> new ArrayList<>())
                .add(binding));
        mailboxes = grouped.entrySet().stream()
                .map(entry -> new PluginEventMailbox(entry.getKey(), entry.getValue(), telemetry))
                .toList();
    }

    public Mono<Void> dispatch(PluginEvent event) {
        if (event == null) {
            return Mono.error(new IllegalArgumentException("Plugin event must not be null"));
        }
        return Mono.fromRunnable(() -> mailboxes.forEach(mailbox -> mailbox.offer(event)));
    }

    public Mono<Void> close() {
        List<PluginEventMailbox> closing;
        synchronized (this) {
            closing = mailboxes;
            mailboxes = List.of();
        }
        return Flux.fromIterable(closing).flatMap(PluginEventMailbox::close).then();
    }
}

package cn.elvis.monaco.plugin.example;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.descriptor.PluginCapability;
import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import cn.elvis.monaco.plugin.api.hook.PluginEventListener;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.api.lifecycle.PluginContext;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class ExamplePlugin implements MonacoPlugin {

    private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
            "example-plugin",
            "Example Plugin",
            "1.0.0",
            ApiVersion.CURRENT,
            Set.of(PluginCapability.EVENT_LISTENER),
            List.of());

    private static final List<PluginHook> HOOKS = List.of(new PluginEventListener() {
        @Override
        public String hookId() {
            return "events";
        }

        @Override
        public Mono<Void> onEvent(cn.elvis.monaco.plugin.api.event.PluginEvent event) {
            return Mono.empty();
        }
    });

    private final AtomicInteger startCount;
    private final AtomicInteger stopCount;

    ExamplePlugin(AtomicInteger startCount, AtomicInteger stopCount) {
        this.startCount = startCount;
        this.stopCount = stopCount;
    }

    @Override
    public PluginDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<PluginHook> hooks() {
        return HOOKS;
    }

    @Override
    public Mono<Void> start(PluginContext context) {
        return Mono.fromRunnable(startCount::incrementAndGet);
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(stopCount::incrementAndGet);
    }
}

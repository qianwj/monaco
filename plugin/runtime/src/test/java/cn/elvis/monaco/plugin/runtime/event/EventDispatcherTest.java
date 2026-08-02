package cn.elvis.monaco.plugin.runtime.event;

import cn.elvis.monaco.plugin.api.event.PluginEvent;
import cn.elvis.monaco.plugin.api.hook.PluginEventListener;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.config.PluginDeployment;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import cn.elvis.monaco.plugin.runtime.telemetry.InMemoryPluginTelemetry;
import cn.elvis.monaco.plugin.runtime.testsupport.PluginTestFixtures;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventDispatcherTest {

    @Test
    void preservesPluginOrderAndDropsLatestOnOverflow() {
        List<String> received = new ArrayList<>();
        Sinks.Empty<Void> firstGate = Sinks.empty();
        PluginEventListener listener = new PluginEventListener() {
            @Override
            public String hookId() {
                return "events";
            }

            @Override
            public Mono<Void> onEvent(PluginEvent event) {
                received.add(event.eventId());
                return "one".equals(event.eventId()) ? firstGate.asMono() : Mono.empty();
            }
        };
        InMemoryPluginTelemetry telemetry = new InMemoryPluginTelemetry();
        PluginDeployment deployment = new PluginDeployment(
                true,
                false,
                100,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1,
                4,
                1,
                EventDropPolicy.DROP_LATEST,
                Map.of());
        PluginHandle handle = PluginTestFixtures.handle(
                "events",
                "1.0.0",
                List.of(),
                deployment,
                List.of(listener),
                PluginTestFixtures.lifecycle(Mono.empty(), Mono.empty()),
                new PluginTestFixtures.DirectPluginInvoker(),
                telemetry);
        handle.start(null).block();
        HookRegistry hooks = new HookRegistry();
        EventDispatcher dispatcher = new EventDispatcher(telemetry);
        dispatcher.publish(hooks.publish(List.of(handle)));

        dispatcher.dispatch(event("one")).block();
        dispatcher.dispatch(event("two")).block();
        dispatcher.dispatch(event("three")).block();
        assertEquals(List.of("one"), received);

        firstGate.tryEmitEmpty();
        assertEquals(List.of("one", "two"), received);
        assertEquals(1, telemetry.snapshot().get("events").droppedEvents());
        dispatcher.close().block();
    }

    private static PluginEvent event(String id) {
        return new PluginEvent.BrokerStarted(id, Instant.EPOCH, "test", "");
    }
}

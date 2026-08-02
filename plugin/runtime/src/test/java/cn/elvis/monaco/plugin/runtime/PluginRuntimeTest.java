package cn.elvis.monaco.plugin.runtime;

import cn.elvis.monaco.plugin.api.context.MessageOrigin;
import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.AuthorizationDecision;
import cn.elvis.monaco.plugin.api.event.PluginEvent;
import cn.elvis.monaco.plugin.api.hook.AuthenticationProvider;
import cn.elvis.monaco.plugin.api.hook.PluginEventListener;
import cn.elvis.monaco.plugin.api.lifecycle.BrokerInfo;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import cn.elvis.monaco.plugin.api.model.PluginSecret;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.config.PluginRuntimeConfig;
import cn.elvis.monaco.plugin.runtime.spi.PluginSource;
import cn.elvis.monaco.plugin.runtime.spi.PluginCandidate;
import cn.elvis.monaco.plugin.runtime.spi.PluginResource;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import cn.elvis.monaco.plugin.runtime.testsupport.PluginTestFixtures;
import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.protocol.model.ClientId;
import cn.elvis.monaco.protocol.model.ConnectionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRuntimeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void startsPublishesHooksAndStopsOwnedResources() {
        List<String> calls = new ArrayList<>();
        PluginPrincipal principal = new PluginPrincipal("alice", Set.of(), Map.of());
        AuthenticationProvider authentication = new AuthenticationProvider() {
            @Override
            public String hookId() {
                return "authentication";
            }

            @Override
            public Mono<AuthenticationDecision> authenticate(
                    PluginRequestContext context, AuthenticationRequest request) {
                return Mono.just(AuthenticationDecision.success(principal));
            }
        };
        PluginEventListener events = new PluginEventListener() {
            @Override
            public String hookId() {
                return "events";
            }

            @Override
            public Mono<Void> onEvent(PluginEvent event) {
                return Mono.fromRunnable(() -> calls.add("event:" + event.eventId()));
            }
        };
        var descriptor = new cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor(
                "runtime-plugin",
                "runtime-plugin",
                "1.0.0",
                ApiVersion.CURRENT,
                Set.of(
                        cn.elvis.monaco.plugin.api.descriptor.PluginCapability.AUTHENTICATION,
                        cn.elvis.monaco.plugin.api.descriptor.PluginCapability.EVENT_LISTENER),
                List.of());
        PluginCandidate candidate = new PluginCandidate(
                descriptor,
                List.of(authentication, events),
                PluginTestFixtures.lifecycle(
                        Mono.fromRunnable(() -> calls.add("start")),
                        Mono.fromRunnable(() -> calls.add("stop"))),
                new PluginTestFixtures.DirectPluginInvoker(),
                "a".repeat(64),
                PluginRuntimeTest.class.getClassLoader(),
                PluginResource.none());
        AtomicBoolean sourceClosed = new AtomicBoolean();
        PluginSource source = new PluginSource() {
            @Override
            public Flux<PluginCandidate> load() {
                return Flux.just(candidate);
            }

            @Override
            public Mono<Void> close() {
                return Mono.fromRunnable(() -> sourceClosed.set(true));
            }
        };
        PluginRuntime runtime = new PluginRuntimeBuilder(
                PluginRuntimeConfig.defaults(temporaryDirectory).withDeployments(Map.of(
                        "runtime-plugin", PluginTestFixtures.deployment(true, 100))),
                new BrokerInfo("test", ApiVersion.CURRENT, "node-1"),
                (context, request) -> Mono.just(AuthenticationDecision.abstain()),
                (context, request) -> Mono.just(AuthorizationDecision.abstain()))
                .source(source)
                .contexts(ignored -> Mono.just(PluginTestFixtures.context()))
                .build();

        runtime.start().block();
        assertTrue(runtime.ready());
        assertEquals(64, runtime.requiredFingerprint().length());
        StepVerifier.create(runtime.authenticate(
                        context(),
                        new AuthenticationRequest(Optional.of("alice"), PluginSecret.empty(), Map.of())))
                .assertNext(decision -> assertEquals(
                        principal, ((AuthenticationDecision.Success) decision).principal()))
                .verifyComplete();
        runtime.publishEvent(new PluginEvent.BrokerStarted(
                "started", Instant.EPOCH, "test", "node-1")).block();
        runtime.stop().block();

        assertEquals(List.of("start", "event:started", "stop"), calls);
        assertTrue(sourceClosed.get());
        assertEquals(PluginRuntimeState.STOPPED, runtime.state());
    }

    private static PluginRequestContext context() {
        return new PluginRequestContext(
                "invocation",
                new ConnectionId("connection"),
                new ClientId("client"),
                Optional.empty(),
                "tcp",
                "127.0.0.1:1000",
                Optional.empty(),
                MessageOrigin.CLIENT,
                "trace",
                Map.of(),
                Instant.EPOCH);
    }
}

package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.MessageOrigin;
import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.EnhancedAuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.hook.EnhancedAuthenticationProvider;
import cn.elvis.monaco.plugin.api.model.EnhancedAuthenticationRequest;
import cn.elvis.monaco.plugin.api.model.PluginSecret;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.registry.EnhancedAuthBindingRegistry;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import cn.elvis.monaco.plugin.runtime.testsupport.PluginTestFixtures;
import cn.elvis.monaco.protocol.model.ClientId;
import cn.elvis.monaco.protocol.model.ConnectionId;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EnhancedAuthenticationChainTest {

    @Test
    void pinsProviderUntilTerminalDecisionAndRejectsStaleGeneration() {
        AtomicInteger calls = new AtomicInteger();
        PluginPrincipal principal = new PluginPrincipal("alice", Set.of(), Map.of());
        EnhancedAuthenticationProvider provider = new EnhancedAuthenticationProvider() {
            @Override
            public String hookId() {
                return "scram";
            }

            @Override
            public String authenticationMethod() {
                return "SCRAM-SHA-256";
            }

            @Override
            public Mono<EnhancedAuthenticationDecision> exchange(
                    PluginRequestContext context,
                    EnhancedAuthenticationRequest request
            ) {
                return Mono.just(switch (calls.incrementAndGet()) {
                    case 1, 3 -> EnhancedAuthenticationDecision.continueWith(
                            authenticationMethod(), PluginSecret.of(new byte[]{1}));
                    default -> EnhancedAuthenticationDecision.success(principal);
                });
            }
        };
        PluginHandle handle = PluginTestFixtures.activeHandle("enhanced", List.of(provider));
        HookRegistry hooks = new HookRegistry();
        hooks.publish(List.of(handle));
        EnhancedAuthBindingRegistry bindings = new EnhancedAuthBindingRegistry();
        EnhancedAuthenticationChain chain = new EnhancedAuthenticationChain(hooks, bindings);

        StepVerifier.create(chain.exchange(context(), request(), Duration.ofSeconds(1)))
                .assertNext(decision -> assertInstanceOf(
                        EnhancedAuthenticationDecision.Continue.class, decision))
                .verifyComplete();
        assertEquals(1, bindings.size());

        StepVerifier.create(chain.exchange(context(), request(), Duration.ofSeconds(1)))
                .assertNext(decision -> assertEquals(
                        principal, ((EnhancedAuthenticationDecision.Success) decision).principal()))
                .verifyComplete();
        assertEquals(0, bindings.size());

        chain.exchange(context(), request(), Duration.ofSeconds(1)).block();
        assertEquals(1, bindings.size());
        hooks.publish(List.of(handle));

        StepVerifier.create(chain.exchange(context(), request(), Duration.ofSeconds(1)))
                .assertNext(decision -> assertEquals(
                        PluginRejectReason.BAD_AUTHENTICATION_METHOD,
                        ((EnhancedAuthenticationDecision.Reject) decision).reason()))
                .verifyComplete();
        assertEquals(0, bindings.size());
        assertEquals(3, calls.get());
    }

    private static EnhancedAuthenticationRequest request() {
        return new EnhancedAuthenticationRequest("SCRAM-SHA-256", PluginSecret.empty(), false);
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

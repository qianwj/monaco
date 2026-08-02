package cn.elvis.monaco.plugin.runtime.chain;

import cn.elvis.monaco.plugin.api.context.MessageOrigin;
import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.AuthorizationDecision;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.hook.AuthenticationProvider;
import cn.elvis.monaco.plugin.api.hook.AuthorizationPolicy;
import cn.elvis.monaco.plugin.api.hook.PublishInboundInterceptor;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import cn.elvis.monaco.plugin.api.model.AuthorizationRequest;
import cn.elvis.monaco.plugin.api.model.PluginPayload;
import cn.elvis.monaco.plugin.api.model.PluginSecret;
import cn.elvis.monaco.plugin.api.model.PublishView;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import cn.elvis.monaco.plugin.runtime.testsupport.PluginTestFixtures;
import cn.elvis.monaco.protocol.model.ClientId;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.TopicName;
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

class HookChainTest {

    @Test
    void authenticationUsesFirstApplicableAndSkipsFallback() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        AtomicInteger finalHookCalls = new AtomicInteger();
        PluginPrincipal principal = new PluginPrincipal("alice", Set.of(), Map.of());
        PluginHandle abstain = PluginTestFixtures.activeHandle(
                "abstain", List.of(authentication("auth-1", Mono.just(AuthenticationDecision.abstain()))));
        PluginHandle success = PluginTestFixtures.activeHandle(
                "success", List.of(authentication("auth-2", Mono.just(AuthenticationDecision.success(principal)))));
        PluginHandle never = PluginTestFixtures.activeHandle(
                "never", List.of(authentication("auth-3", Mono.fromSupplier(() -> {
                    finalHookCalls.incrementAndGet();
                    return AuthenticationDecision.reject(PluginRejectReason.BAD_CREDENTIALS, "");
                }))));
        HookRegistry registry = registry(abstain, success, never);
        AuthenticationChain chain = new AuthenticationChain(registry, (context, request) -> {
            fallbackCalls.incrementAndGet();
            return Mono.just(AuthenticationDecision.reject(PluginRejectReason.BAD_CREDENTIALS, ""));
        });

        StepVerifier.create(chain.authenticate(context(), credentials(), Duration.ofSeconds(1)))
                .assertNext(decision -> assertEquals(
                        principal, ((AuthenticationDecision.Success) decision).principal()))
                .verifyComplete();

        assertEquals(0, finalHookCalls.get());
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void authorizationUsesDenyOverrides() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        PluginHandle allow = PluginTestFixtures.activeHandle(
                "allow", List.of(authorization("allow", AuthorizationDecision.allow())));
        PluginHandle deny = PluginTestFixtures.activeHandle(
                "deny", List.of(authorization(
                        "deny", AuthorizationDecision.deny(PluginRejectReason.NOT_AUTHORIZED, "denied"))));
        AuthorizationChain chain = new AuthorizationChain(registry(allow, deny), (context, request) -> {
            fallbackCalls.incrementAndGet();
            return Mono.just(AuthorizationDecision.allow());
        });

        StepVerifier.create(chain.authorize(
                        context(), new AuthorizationRequest.Publish(publication(false)), Duration.ofSeconds(1)))
                .assertNext(decision -> assertInstanceOf(AuthorizationDecision.Deny.class, decision))
                .verifyComplete();
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void publishChainAppliesSequentialChangesAndRejectsForbiddenIdentityChanges() {
        PublishInboundInterceptor rewrite = publishHook("rewrite", (context, value) -> Mono.just(
                PolicyDecision.allow(value.withMessage(
                        new TopicName("rewritten/topic"),
                        PluginPayload.of(new byte[]{2}),
                        value.qos(),
                        value.userProperties()))));
        PublishInboundInterceptor observe = publishHook("observe", (context, value) -> {
            assertEquals(new TopicName("rewritten/topic"), value.topic());
            return Mono.just(PolicyDecision.allow(value, Map.of("risk", "low")));
        });
        PublishInterceptorChain validChain = new PublishInterceptorChain(registry(
                PluginTestFixtures.activeHandle("rewrite", List.of(rewrite)),
                PluginTestFixtures.activeHandle("observe", List.of(observe))));

        StepVerifier.create(validChain.intercept(context(), publication(false), Duration.ofSeconds(1)))
                .assertNext(decision -> {
                    PolicyDecision.Allow<PublishView> allowed =
                            (PolicyDecision.Allow<PublishView>) decision;
                    assertEquals(new TopicName("rewritten/topic"), allowed.value().topic());
                    assertEquals(Map.of("observe.risk", "low"), allowed.attributes());
                })
                .verifyComplete();

        PublishInboundInterceptor forbidden = publishHook("forbidden", (context, value) -> Mono.just(
                PolicyDecision.allow(publication(true))));
        PublishInterceptorChain forbiddenChain = new PublishInterceptorChain(registry(
                PluginTestFixtures.activeHandle("forbidden", List.of(forbidden))));
        StepVerifier.create(forbiddenChain.intercept(context(), publication(false), Duration.ofSeconds(1)))
                .assertNext(decision -> assertInstanceOf(PolicyDecision.Reject.class, decision))
                .verifyComplete();
    }

    private static HookRegistry registry(PluginHandle... handles) {
        HookRegistry registry = new HookRegistry();
        registry.publish(List.of(handles));
        return registry;
    }

    private static AuthenticationProvider authentication(
            String id,
            Mono<AuthenticationDecision> decision
    ) {
        return new AuthenticationProvider() {
            @Override
            public String hookId() {
                return id;
            }

            @Override
            public Mono<AuthenticationDecision> authenticate(
                    PluginRequestContext context, AuthenticationRequest request) {
                return decision;
            }
        };
    }

    private static AuthorizationPolicy authorization(String id, AuthorizationDecision decision) {
        return new AuthorizationPolicy() {
            @Override
            public String hookId() {
                return id;
            }

            @Override
            public Mono<AuthorizationDecision> authorize(
                    PluginRequestContext context, AuthorizationRequest request) {
                return Mono.just(decision);
            }
        };
    }

    private static PublishInboundInterceptor publishHook(
            String id,
            java.util.function.BiFunction<PluginRequestContext, PublishView, Mono<PolicyDecision<PublishView>>> call
    ) {
        return new PublishInboundInterceptor() {
            @Override
            public String hookId() {
                return id;
            }

            @Override
            public Mono<PolicyDecision<PublishView>> intercept(
                    PluginRequestContext context, PublishView publication) {
                return call.apply(context, publication);
            }
        };
    }

    private static AuthenticationRequest credentials() {
        return new AuthenticationRequest(Optional.of("alice"), PluginSecret.empty(), Map.of());
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

    private static PublishView publication(boolean duplicate) {
        return new PublishView(
                new TopicName("original/topic"),
                PluginPayload.of(new byte[]{1}),
                QoS.AT_MOST_ONCE,
                false,
                duplicate,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                MessageOrigin.CLIENT);
    }
}

package cn.elvis.monaco.auth.simple;

import cn.elvis.monaco.auth.credential.CredentialProvider;
import cn.elvis.monaco.auth.credential.StoredCredential;
import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import cn.elvis.monaco.plugin.api.model.PluginSecret;
import cn.elvis.monaco.protocol.model.ClientId;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.plugin.api.context.MessageOrigin;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SimpleAuthProviderTest {

    private final CredentialProvider credentials = new CredentialProvider() {
        @Override
        public String type() { return "test"; }

        @Override
        public Mono<StoredCredential> lookup(String username) {
            if ("alice".equals(username)) {
                return Mono.just(StoredCredential.plainText("alice", "secret123"));
            }
            return Mono.empty();
        }
    };

    private final SimpleAuthProvider provider =
            new SimpleAuthProvider(credentials, new PlainPasswordEncoder());

    private PluginRequestContext dummyContext() {
        return new PluginRequestContext(
                "inv-1",
                new ConnectionId("conn-1"),
                new ClientId("client-1"),
                Optional.empty(),
                "tcp",
                "127.0.0.1",
                Optional.empty(),
                MessageOrigin.CLIENT,
                "trace-1",
                Map.of(),
                Instant.now()
        );
    }

    @Test
    void successfulAuthentication() {
        AuthenticationRequest request = new AuthenticationRequest(
                Optional.of("alice"),
                PluginSecret.of("secret123".getBytes()),
                Map.of()
        );

        StepVerifier.create(provider.authenticate(dummyContext(), request))
                .assertNext(decision -> {
                    assertInstanceOf(AuthenticationDecision.Success.class, decision);
                    PluginPrincipal principal =
                            ((AuthenticationDecision.Success) decision).principal();
                    assertEquals("alice", principal.name());
                })
                .verifyComplete();
    }

    @Test
    void wrongPasswordRejects() {
        AuthenticationRequest request = new AuthenticationRequest(
                Optional.of("alice"),
                PluginSecret.of("wrong".getBytes()),
                Map.of()
        );

        StepVerifier.create(provider.authenticate(dummyContext(), request))
                .assertNext(decision ->
                        assertInstanceOf(AuthenticationDecision.Reject.class, decision))
                .verifyComplete();
    }

    @Test
    void unknownUserRejects() {
        AuthenticationRequest request = new AuthenticationRequest(
                Optional.of("unknown"),
                PluginSecret.of("pass".getBytes()),
                Map.of()
        );

        StepVerifier.create(provider.authenticate(dummyContext(), request))
                .assertNext(decision ->
                        assertInstanceOf(AuthenticationDecision.Reject.class, decision))
                .verifyComplete();
    }

    @Test
    void noUsernameAbstains() {
        AuthenticationRequest request = new AuthenticationRequest(
                Optional.empty(),
                PluginSecret.of("pass".getBytes()),
                Map.of()
        );

        StepVerifier.create(provider.authenticate(dummyContext(), request))
                .assertNext(decision ->
                        assertInstanceOf(AuthenticationDecision.Abstain.class, decision))
                .verifyComplete();
    }
}

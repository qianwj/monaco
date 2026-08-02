package cn.elvis.monaco.core.auth;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreAuthenticatorTest {

    @Test
    void envProvider_authenticatesSuccessfully() {
        var provider = new EnvCredentialProvider(key ->
                "MQTT_USER_ADMIN".equals(key) ? "secret" : null);
        var auth = CoreAuthenticator.create(provider);

        StepVerifier.create(auth.authenticate("admin", "secret".getBytes()))
                .assertNext(r -> assertInstanceOf(CoreAuthenticator.AuthResult.Success.class, r))
                .verifyComplete();
    }

    @Test
    void envProvider_rejectsWrongPassword() {
        var provider = new EnvCredentialProvider(key ->
                "MQTT_USER_ADMIN".equals(key) ? "secret" : null);
        var auth = CoreAuthenticator.create(provider);

        StepVerifier.create(auth.authenticate("admin", "wrong".getBytes()))
                .assertNext(r -> {
                    var reject = assertInstanceOf(CoreAuthenticator.AuthResult.Reject.class, r);
                    assertEquals("Bad credentials", reject.reason());
                })
                .verifyComplete();
    }

    @Test
    void envProvider_rejectsUnknownUser() {
        var provider = new EnvCredentialProvider(key -> null);
        var auth = CoreAuthenticator.create(provider);

        StepVerifier.create(auth.authenticate("nobody", "pass".getBytes()))
                .assertNext(r -> {
                    var reject = assertInstanceOf(CoreAuthenticator.AuthResult.Reject.class, r);
                    assertEquals("Unknown user", reject.reason());
                })
                .verifyComplete();
    }

    @Test
    void fileProvider_authenticatesWithPlain() {
        var provider = new FileCredentialProvider(List.of(
                "alice:plain:password123",
                "# comment line",
                "",
                "bob:sha256:invalid_not_tested_here"
        ));
        var auth = CoreAuthenticator.create(provider);

        StepVerifier.create(auth.authenticate("alice", "password123".getBytes()))
                .assertNext(r -> {
                    var success = assertInstanceOf(CoreAuthenticator.AuthResult.Success.class, r);
                    assertEquals("alice", success.username());
                })
                .verifyComplete();
    }

    @Test
    void fileProvider_authenticatesWithSha256() {
        // sha256("mypassword") = 89e01536ac207279409d4de1e5253e01f4a1769e696db0d6062ca9b8f56767c8
        var provider = new FileCredentialProvider(List.of(
                "bob:sha256:89e01536ac207279409d4de1e5253e01f4a1769e696db0d6062ca9b8f56767c8"
        ));
        var auth = CoreAuthenticator.create(provider);

        StepVerifier.create(auth.authenticate("bob", "mypassword".getBytes()))
                .assertNext(r -> assertInstanceOf(CoreAuthenticator.AuthResult.Success.class, r))
                .verifyComplete();
    }

    @Test
    void rejectsMissingUsername() {
        var auth = CoreAuthenticator.create(new EnvCredentialProvider(k -> null));

        StepVerifier.create(auth.authenticate(null, "pass".getBytes()))
                .assertNext(r -> assertInstanceOf(CoreAuthenticator.AuthResult.Reject.class, r))
                .verifyComplete();
    }

    @Test
    void rejectsMissingPassword() {
        var auth = CoreAuthenticator.create(new EnvCredentialProvider(k -> "x"));

        StepVerifier.create(auth.authenticate("user", null))
                .assertNext(r -> assertInstanceOf(CoreAuthenticator.AuthResult.Reject.class, r))
                .verifyComplete();
    }
}

package cn.elvis.monaco.auth.credential.env;

import cn.elvis.monaco.auth.credential.StoredCredential;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvCredentialProviderTest {

    private final Map<String, String> fakeEnv = Map.of(
            "MQTT_USER_ALICE", "secret123",
            "MQTT_USER_BOB", "pass456"
    );

    private final EnvCredentialProvider provider = new EnvCredentialProvider(fakeEnv::get);

    @Test
    void type() {
        assertEquals("env", provider.type());
    }

    @Test
    void lookupExistingUser() {
        StepVerifier.create(provider.lookup("alice"))
                .assertNext(stored -> {
                    assertEquals("alice", stored.username());
                    assertEquals("secret123", stored.hashedPassword());
                    assertNull(stored.salt());
                    assertEquals(0, stored.iterations());
                })
                .verifyComplete();
    }

    @Test
    void lookupIsCaseInsensitive() {
        StepVerifier.create(provider.lookup("Alice"))
                .assertNext(stored -> assertEquals("secret123", stored.hashedPassword()))
                .verifyComplete();
    }

    @Test
    void lookupUnknownUserReturnsEmpty() {
        StepVerifier.create(provider.lookup("unknown"))
                .verifyComplete();
    }

    @Test
    void lookupNullUsernameReturnsEmpty() {
        StepVerifier.create(provider.lookup(null))
                .verifyComplete();
    }

    @Test
    void lookupBlankUsernameReturnsEmpty() {
        StepVerifier.create(provider.lookup("  "))
                .verifyComplete();
    }
}

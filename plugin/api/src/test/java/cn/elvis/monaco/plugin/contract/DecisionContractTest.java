package cn.elvis.monaco.plugin.contract;

import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.AuthorizationDecision;
import cn.elvis.monaco.plugin.api.decision.EnhancedAuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.PluginRejectReason;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.model.PluginSecret;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionContractTest {

    @Test
    void createsTypedAuthenticationResults() {
        PluginPrincipal principal = new PluginPrincipal("alice", Set.of("operator"), Map.of());

        assertEquals(principal,
                ((AuthenticationDecision.Success) AuthenticationDecision.success(principal)).principal());
        assertEquals(PluginRejectReason.BAD_CREDENTIALS,
                ((AuthenticationDecision.Reject) AuthenticationDecision.reject(
                        PluginRejectReason.BAD_CREDENTIALS, "invalid credentials")).reason());

        EnhancedAuthenticationDecision.Continue continuation =
                (EnhancedAuthenticationDecision.Continue)
                        EnhancedAuthenticationDecision.continueWith(
                                "SCRAM-SHA-256", PluginSecret.of(new byte[]{1, 2}));
        assertEquals("SCRAM-SHA-256", continuation.authenticationMethod());
        assertEquals(2, continuation.authenticationData().size());
        assertEquals(principal,
                ((EnhancedAuthenticationDecision.Success)
                        EnhancedAuthenticationDecision.success(principal)).principal());
    }

    @Test
    void normalizesAndBoundsPublicMessages() {
        AuthorizationDecision.Deny deny = (AuthorizationDecision.Deny)
                AuthorizationDecision.deny(PluginRejectReason.NOT_AUTHORIZED, null);
        assertEquals("", deny.publicMessage());

        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationDecision.deny(PluginRejectReason.NOT_AUTHORIZED, "x".repeat(1_025)));
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationDecision.deny(PluginRejectReason.NOT_AUTHORIZED, "bad\0message"));
    }

    @Test
    void policyDecisionCopiesNamespacedAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("risk", "low");
        PolicyDecision.Allow<String> allow = (PolicyDecision.Allow<String>)
                PolicyDecision.allow("value", attributes);
        attributes.clear();

        assertEquals(Map.of("risk", "low"), allow.attributes());
        assertThrows(UnsupportedOperationException.class, () -> allow.attributes().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> PolicyDecision.allow(null));
    }
}

package cn.elvis.monaco.plugin.api.decision;

import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.api.model.PluginSecret;

/** Result of one MQTT 5 enhanced-authentication exchange. */
public sealed interface EnhancedAuthenticationDecision {

    record Continue(String authenticationMethod, PluginSecret authenticationData)
            implements EnhancedAuthenticationDecision {
        public Continue {
            if (authenticationMethod == null || authenticationMethod.isBlank()) {
                throw new IllegalArgumentException("Authentication method must not be blank");
            }
            authenticationData = authenticationData == null
                    ? PluginSecret.empty()
                    : authenticationData;
        }
    }

    record Success(PluginPrincipal principal) implements EnhancedAuthenticationDecision {
        public Success {
            if (principal == null) {
                throw new IllegalArgumentException("Authentication principal must not be null");
            }
        }
    }

    record Reject(PluginRejectReason reason, String publicMessage)
            implements EnhancedAuthenticationDecision {
        public Reject {
            if (reason == null) {
                throw new IllegalArgumentException("Authentication reject reason must not be null");
            }
            publicMessage = DecisionValidation.publicMessage(publicMessage);
        }
    }

    static EnhancedAuthenticationDecision continueWith(String method, PluginSecret data) {
        return new Continue(method, data);
    }

    static EnhancedAuthenticationDecision success(PluginPrincipal principal) {
        return new Success(principal);
    }

    static EnhancedAuthenticationDecision reject(PluginRejectReason reason, String publicMessage) {
        return new Reject(reason, publicMessage);
    }
}

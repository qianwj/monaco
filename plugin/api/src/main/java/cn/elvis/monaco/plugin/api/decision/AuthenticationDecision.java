package cn.elvis.monaco.plugin.api.decision;

import cn.elvis.monaco.plugin.api.context.PluginPrincipal;

/** Result of a basic CONNECT authentication provider. */
public sealed interface AuthenticationDecision {

    record Abstain() implements AuthenticationDecision { }

    record Success(PluginPrincipal principal) implements AuthenticationDecision {
        public Success {
            if (principal == null) {
                throw new IllegalArgumentException("Authentication principal must not be null");
            }
        }
    }

    record Reject(PluginRejectReason reason, String publicMessage)
            implements AuthenticationDecision {
        public Reject {
            if (reason == null) {
                throw new IllegalArgumentException("Authentication reject reason must not be null");
            }
            publicMessage = DecisionValidation.publicMessage(publicMessage);
        }
    }

    static AuthenticationDecision abstain() {
        return new Abstain();
    }

    static AuthenticationDecision success(PluginPrincipal principal) {
        return new Success(principal);
    }

    static AuthenticationDecision reject(PluginRejectReason reason, String publicMessage) {
        return new Reject(reason, publicMessage);
    }
}

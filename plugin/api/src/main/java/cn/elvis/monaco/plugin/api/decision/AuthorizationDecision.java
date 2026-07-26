package cn.elvis.monaco.plugin.api.decision;

/** Result of an authorization policy. Chain merging uses deny-overrides. */
public sealed interface AuthorizationDecision {

    record Abstain() implements AuthorizationDecision { }

    record Allow() implements AuthorizationDecision { }

    record Deny(PluginRejectReason reason, String publicMessage)
            implements AuthorizationDecision {
        public Deny {
            if (reason == null) {
                throw new IllegalArgumentException("Authorization deny reason must not be null");
            }
            publicMessage = DecisionValidation.publicMessage(publicMessage);
        }
    }

    static AuthorizationDecision abstain() {
        return new Abstain();
    }

    static AuthorizationDecision allow() {
        return new Allow();
    }

    static AuthorizationDecision deny(PluginRejectReason reason, String publicMessage) {
        return new Deny(reason, publicMessage);
    }
}

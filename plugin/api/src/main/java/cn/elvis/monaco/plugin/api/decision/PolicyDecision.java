package cn.elvis.monaco.plugin.api.decision;

import java.util.Map;

/** Result of a connection, publish, subscription, or Will interceptor. */
public sealed interface PolicyDecision<T> {

    record Allow<T>(T value, Map<String, String> attributes) implements PolicyDecision<T> {
        public Allow {
            if (value == null) {
                throw new IllegalArgumentException("Allowed plugin value must not be null");
            }
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    record Reject<T>(PluginRejectReason reason, String publicMessage)
            implements PolicyDecision<T> {
        public Reject {
            if (reason == null) {
                throw new IllegalArgumentException("Policy reject reason must not be null");
            }
            publicMessage = DecisionValidation.publicMessage(publicMessage);
        }
    }

    static <T> PolicyDecision<T> allow(T value) {
        return new Allow<>(value, Map.of());
    }

    static <T> PolicyDecision<T> allow(T value, Map<String, String> attributes) {
        return new Allow<>(value, attributes);
    }

    static <T> PolicyDecision<T> reject(PluginRejectReason reason, String publicMessage) {
        return new Reject<>(reason, publicMessage);
    }
}

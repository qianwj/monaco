package cn.elvis.monaco.core.limits;

import cn.elvis.monaco.protocol.model.QoS;

import java.time.Duration;

/**
 * Immutable protocol-level limits and capabilities.
 * <p>
 * Only contains fields consumed by domain Transitions.
 * Transport, auth, storage, and metrics configs belong to their respective adapter modules.
 */
public record ProtocolLimits(
        Duration maxSessionExpiryInterval,
        int defaultReceiveMaximum,
        int maxReceiveMaximum,
        int topicAliasMaximum,
        int maxPacketSize,
        Duration serverKeepAlive,
        QoS maximumQoS,
        boolean retainAvailable,
        boolean wildcardSubscriptionAvailable,
        boolean subscriptionIdentifierAvailable,
        boolean sharedSubscriptionAvailable
) {

    /**
     * Keep Alive timeout = 1.5x serverKeepAlive (MQTT 5.0 §3.1.2.10).
     * Returns ZERO when serverKeepAlive is ZERO (not enforced).
     */
    public Duration keepAliveTimeout() {
        if (serverKeepAlive.isZero()) {
            return Duration.ZERO;
        }
        return serverKeepAlive.plus(serverKeepAlive.dividedBy(2));
    }
}

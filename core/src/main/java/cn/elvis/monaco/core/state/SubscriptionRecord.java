package cn.elvis.monaco.core.state;

import cn.elvis.monaco.protocol.model.QoS;

import java.time.Instant;

/**
 * Persistent subscription record — stored independently per (clientId, topicFilter).
 */
public record SubscriptionRecord(
        String clientId,
        String topicFilter,
        QoS maxQoS,
        boolean noLocal,
        boolean retainAsPublished,
        int retainHandling,
        int subscriptionIdentifier,
        long revision,
        Instant createdAt
) {
}

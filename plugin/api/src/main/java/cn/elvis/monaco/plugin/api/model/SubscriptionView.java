package cn.elvis.monaco.plugin.api.model;

import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.TopicFilter;
import cn.elvis.monaco.protocol.packet.Subscription.RetainHandling;

import java.util.List;

/** One SUBSCRIBE entry processed independently while preserving request order. */
public record SubscriptionView(
        TopicFilter topicFilter,
        QoS maximumQos,
        boolean noLocal,
        boolean retainAsPublished,
        RetainHandling retainHandling,
        List<Integer> subscriptionIdentifiers
) {

    public SubscriptionView {
        if (topicFilter == null || maximumQos == null || retainHandling == null) {
            throw new IllegalArgumentException("Subscription view fields must not be null");
        }
        subscriptionIdentifiers = subscriptionIdentifiers == null
                ? List.of()
                : List.copyOf(subscriptionIdentifiers);
        if (subscriptionIdentifiers.stream().anyMatch(id -> id == null || id < 1 || id > 268_435_455)) {
            throw new IllegalArgumentException("Subscription identifiers must be 1-268435455");
        }
    }

    public SubscriptionView withMaximumQos(QoS grantedQos) {
        if (grantedQos == null || grantedQos.value() > maximumQos.value()) {
            throw new IllegalArgumentException("Plugin must not increase subscription QoS");
        }
        return new SubscriptionView(topicFilter, grantedQos, noLocal, retainAsPublished,
                retainHandling, subscriptionIdentifiers);
    }
}

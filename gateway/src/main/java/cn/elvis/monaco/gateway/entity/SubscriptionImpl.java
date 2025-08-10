package cn.elvis.monaco.gateway.entity;

import cn.elvis.monaco.gateway.session.ClientSession;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.vertx.mqtt.MqttTopicSubscription;

import java.util.Objects;
import java.util.Optional;

/**
 * Subscription implementation.
 * @author qianwj
 * @since  0.0.1
 */
public final class SubscriptionImpl implements Subscription {

    private final ClientSession clientSession;

    private final String topicFilter;

    private final MqttQoS qos;

    private final boolean noLocal;

    private final boolean retainAsPublished;

    private final MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy;

    SubscriptionImpl(ClientSession clientSession, MqttTopicSubscription subscription) {
        Objects.requireNonNull(clientSession, "clientSession must not be null");
        Objects.requireNonNull(subscription, "subscription must not be null");
        this.clientSession = clientSession;
        this.topicFilter = subscription.topicName();
        this.qos = subscription.qualityOfService();
        this.noLocal = subscription.subscriptionOption().isNoLocal();
        this.retainAsPublished = subscription.subscriptionOption().isRetainAsPublished();
        this.retainedHandlingPolicy = Optional.ofNullable(subscription.subscriptionOption())
                .map(MqttSubscriptionOption::retainHandling)
                .orElse(MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE);
    }

    @Override
    public String topicFilter() {
        return topicFilter;
    }

    @Override
    public MqttQoS qos() {
        return qos;
    }

    @Override
    public String sessionId() {
        return clientSession.identifier();
    }

    public boolean noLocal() {
        return noLocal;
    }

    public MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy() {
        return retainedHandlingPolicy;
    }

    public boolean retainAsPublished() {
        return retainAsPublished;
    }

    @Override
    public ClientSession subscriber() {
        return clientSession;
    }
}

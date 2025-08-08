package cn.elvis.monaco.gateway.entity;

import cn.elvis.monaco.gateway.session.ClientSession;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttTopicSubscription;

import java.util.Objects;

public final class SubscriptionImpl implements Subscription {

    private final ClientSession clientSession;

    private final String topicFilter;

    private final MqttQoS qos;

    SubscriptionImpl(ClientSession clientSession, MqttTopicSubscription subscription) {
        Objects.requireNonNull(clientSession, "clientSession must not be null");
        Objects.requireNonNull(subscription, "subscription must not be null");
        this.clientSession = clientSession;
        this.topicFilter = subscription.topicName();
        this.qos = subscription.qualityOfService();
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

    @Override
    public ClientSession subscriber() {
        return clientSession;
    }
}

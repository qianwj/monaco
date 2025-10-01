package cn.elvis.monaco.entity;

import cn.elvis.monaco.session.ClientSession;
import cn.elvis.monaco.topics.Topic;
import cn.elvis.monaco.topics.Topics;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.vertx.mqtt.MqttTopicSubscription;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Subscription implementation.
 * @author qianwj
 * @since  0.0.1
 */
public final class SubscriptionImpl implements Subscription {

    private final String clientId;

    private final Topic topic;

    private final MqttQoS qos;

    private final boolean noLocal;

    private final boolean retainAsPublished;

    private final MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy;

    SubscriptionImpl(String clientId,
                     MqttQoS qos,
                     MqttTopicSubscription subscription) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(subscription, "subscription must not be null");
        this.clientId = clientId;
        this.topic = Topics.createTopic(subscription.topicName());
        this.qos = qos;
        this.noLocal = subscription.subscriptionOption().isNoLocal();
        this.retainAsPublished = subscription.subscriptionOption().isRetainAsPublished();
        this.retainedHandlingPolicy = Optional.ofNullable(subscription.subscriptionOption())
                .map(MqttSubscriptionOption::retainHandling)
                .orElse(MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE);
    }

    @Override
    public Topic topic() {
        return topic;
    }

    @Override
    public MqttQoS qos() {
        return qos;
    }

    @Override
    public String clientIdentifier() {
        return clientId;
    }

    @Override
    public boolean noLocal() {
        return noLocal;
    }

    @Override
    public MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy() {
        return retainedHandlingPolicy;
    }

    @Override
    public boolean retainAsPublished() {
        return retainAsPublished;
    }

    @Override
    public String topicFilter() {
        return topic.unwrap();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Subscription.class.getSimpleName() + "[", "]")
                .add("clientId='" + clientId + "'")
                .add("topic=" + topic)
                .add("qos=" + qos)
                .add("noLocal=" + noLocal)
                .add("retainAsPublished=" + retainAsPublished)
                .add("retainedHandlingPolicy=" + retainedHandlingPolicy)
                .toString();
    }
}

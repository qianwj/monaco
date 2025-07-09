package cn.elvis.monaco.topics;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.vertx.mqtt.MqttTopicSubscription;

public record Subscription(
        String sessionId,
        int subscriptionId,
        String topicFilter,
        MqttQoS qos,
        boolean noLocal,
        boolean retainAsPublished,
        MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy
) {

    public static Subscription from(String sessionId, int subscriptionId, MqttTopicSubscription subscription) {
        var option = subscription.subscriptionOption();
        return new Subscription(
                sessionId,
                subscriptionId,
                subscription.topicName(),
                option.qos(),
                option.isNoLocal(),
                option.isRetainAsPublished(),
                option.retainHandling()
        );
    }

    public int qosValue() {
        return qos.value();
    }

    public boolean invalidTopicFilter() {
        //todo: validate topic filter
        return false;
    }

    public boolean isShared() {
        // todo: is shared topic;
        return false;
    }

    public boolean isWildcard() {
        // todo: is wildcard topic;
        return false;
    }
}

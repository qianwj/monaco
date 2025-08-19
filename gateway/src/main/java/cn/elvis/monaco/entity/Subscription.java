package cn.elvis.monaco.entity;

import cn.elvis.monaco.topics.Topic;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.vertx.mqtt.MqttTopicSubscription;

/**
 * Topic Subscription
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface Subscription {

    Topic topic();

    MqttQoS qos();

    String clientId();

    boolean noLocal();

    MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy();

    boolean retainAsPublished();

    String topicFilter();

    static Subscription of(String clientId, MqttQoS qos, MqttTopicSubscription subscription) {
        return new SubscriptionImpl(clientId, qos, subscription);
    }
}

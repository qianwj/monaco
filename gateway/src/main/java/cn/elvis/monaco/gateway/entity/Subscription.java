package cn.elvis.monaco.gateway.entity;

import cn.elvis.monaco.gateway.session.ClientSession;
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

    String topicFilter();

    MqttQoS qos();

    String sessionId();

    boolean noLocal();

    MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy();

    boolean retainAsPublished();

    ClientSession subscriber();

    static Subscription of(ClientSession clientSession, MqttTopicSubscription subscription) {
        return new SubscriptionImpl(clientSession, subscription);
    }
}

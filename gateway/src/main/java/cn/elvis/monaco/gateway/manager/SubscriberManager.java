package cn.elvis.monaco.gateway.manager;

import cn.elvis.monaco.gateway.entity.Subscription;
import cn.elvis.monaco.gateway.session.ClientSession;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

import java.util.List;
import java.util.function.Consumer;

/**
 * Managing subscribers by subscribed topics.
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface SubscriberManager extends Manager {

    MqttSubAckReasonCode subscribe(ClientSession clientSession, Subscription subscription);

    MqttUnsubAckReasonCode unsubscribe(ClientSession clientSession, String topicFilter);

    void search(String filter, Consumer<Subscription> consumer);

    boolean exists(String filter);
}

package cn.elvis.monaco.manager;

import cn.elvis.monaco.entity.ack.SubscribeAcknowledge;
import cn.elvis.monaco.entity.ack.UnsubscribeAcknowledge;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

/**
 * Managing subscribers by subscribed topics.
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface SubscriberManager extends Manager {

    SubscribeAcknowledge subscribe(MqttEndpoint endpoint, MqttSubscribeMessage packet);

    UnsubscribeAcknowledge unsubscribe(MqttEndpoint endpoint, MqttUnsubscribeMessage packet);

    boolean exists(String filter);
}

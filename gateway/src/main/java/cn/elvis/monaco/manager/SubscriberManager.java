package cn.elvis.monaco.manager;

import cn.elvis.monaco.entity.SubscribeAcknowledge;
import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.entity.UnsubscribeAcknowledge;
import cn.elvis.monaco.session.ClientSession;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

import java.util.function.Consumer;

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

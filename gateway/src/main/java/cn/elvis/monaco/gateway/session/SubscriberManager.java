package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.entity.Subscription;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

import java.util.function.Consumer;

/**
 * Managing subscribers by subscribed topics.
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface SubscriberManager {

    MqttSubAckReasonCode subscribe(ClientSession clientSession, Subscription subscription);

    void search(String filter, Consumer<Subscription> consumer);

    void close();
}

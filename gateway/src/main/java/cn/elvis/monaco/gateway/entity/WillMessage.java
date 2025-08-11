package cn.elvis.monaco.gateway.entity;

import io.vertx.mqtt.MqttWill;

import java.time.Duration;

/**
 * Will Message
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface WillMessage {

    Duration delayInterval();

    PublishMessage body();

    static WillMessage create(MqttWill will) {
        return new WillMessageImpl(will);
    }
}

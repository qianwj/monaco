package cn.elvis.monaco.gateway.entity;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttWill;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * publish message
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface PublishMessage {

    int packetId();

    MqttQoS qos();

    String topic();

    Buffer payload();

    boolean duplicate();

    boolean retain();

    boolean expired();

    MqttProperties properties();

    PublishMessage setRetain(boolean retain);

    PublishMessage setTopic(String topic);

    static PublishMessage of(MqttPublishMessage mqttPublishMessage) {
        return new PublishMessageImpl(mqttPublishMessage);
    }

    static PublishMessage of(MqttPublishMessage source, String topic, boolean duplicate, boolean retain) {
        return new PublishMessageImpl(source, topic, duplicate, retain);
    }

    static PublishMessage of(int packetId, MqttWill will) {
        return new PublishMessageImpl(packetId, will);
    }
}

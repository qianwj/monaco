package cn.elvis.monaco.entity;

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

    String senderId();

    MqttQoS qos();

    String topic();

    Buffer payload();

    boolean duplicate();

    boolean retain();

    boolean expired();

    MqttProperties properties();

    static PublishMessage of(MqttPublishMessage mqttPublishMessage, String senderId) {
        return new PublishMessageImpl(mqttPublishMessage, senderId);
    }

    static PublishMessage of(MqttPublishMessage source, String topic, MqttQoS qos, boolean duplicate, boolean retain, String senderId) {
        return new PublishMessageImpl(source, topic, qos, duplicate, retain, senderId);
    }

    static PublishMessage of(int packetId, String senderId, MqttWill will) {
        return new PublishMessageImpl(packetId, senderId, will);
    }
}

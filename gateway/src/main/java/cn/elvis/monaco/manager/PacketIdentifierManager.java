package cn.elvis.monaco.manager;

import cn.elvis.monaco.entity.ack.Acknowledge;
import io.vertx.mqtt.messages.MqttMessage;

import java.util.Optional;

public interface PacketIdentifierManager extends Manager {

    Optional<Acknowledge> setUsingPacketId(String clientId, MqttMessage packet);

    void unsetUsingPacketId(String clientId, int packetId);
}

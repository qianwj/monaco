package cn.elvis.monaco.manager;

import cn.elvis.monaco.entity.ack.PublishComplete;
import cn.elvis.monaco.entity.ack.PublishExchangeAcknowledge;
import cn.elvis.monaco.entity.ack.PublishRelease;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

public interface PublisherManager extends Manager {

    PublishExchangeAcknowledge publish(MqttEndpoint endpoint, MqttPublishMessage packet);

    PublishRelease publishReceived(MqttEndpoint endpoint, int packetId);

    PublishComplete releasePublish(int packetId);
}

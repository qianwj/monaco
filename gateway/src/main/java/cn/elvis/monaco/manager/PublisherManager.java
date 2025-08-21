package cn.elvis.monaco.manager;

import cn.elvis.monaco.entity.ack.PublishExchangeAcknowledge;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

public interface PublisherManager extends Manager {

    PublishExchangeAcknowledge publish(MqttEndpoint endpoint, MqttPublishMessage packet);
}

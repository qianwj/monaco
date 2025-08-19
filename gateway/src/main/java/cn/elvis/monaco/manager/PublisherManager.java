package cn.elvis.monaco.manager;

import cn.elvis.monaco.entity.PublishExchangeAcknowledge;
import cn.elvis.monaco.entity.PublishMessage;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

import java.util.function.Predicate;

public interface PublisherManager extends Manager {

    PublishExchangeAcknowledge publish(MqttEndpoint endpoint, MqttPublishMessage packet);
}

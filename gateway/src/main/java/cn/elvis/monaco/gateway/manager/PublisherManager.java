package cn.elvis.monaco.gateway.manager;

import cn.elvis.monaco.gateway.entity.PublishMessage;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

import java.util.function.Predicate;

public interface PublisherManager extends Manager {

    Future<PublishMessage> publish(MqttEndpoint endpoint,
                                   MqttPublishMessage packet,
                                   Predicate<String> subscriberExists);

    void reject(MqttEndpoint endpoint, Throwable exception, MqttPublishMessage packet);
}

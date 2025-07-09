package cn.elvis.monaco.session;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

final class Publisher {

    private final MqttEndpoint endpoint;

    private final Vertx vertx;

    private final OperateChecker checker;

    Publisher(MqttEndpoint endpoint, Vertx vertx, OperateChecker checker) {
        this.endpoint = endpoint;
        this.vertx = vertx;
        this.checker = checker;
        endpoint.publishAutoAck(false)
                .publishHandler(handlePublish())
                .publishAcknowledgeHandler(handlePublishAck())
                .publishReceivedHandler(handlePublishReceived())
                .publishCompletionHandler(handlePublishCompletionHandler());
    }

    void publish(MqttPublishMessage message) {
        endpoint.publish(
                message.topicName(),
                message.payload(),
                message.qosLevel(),
                message.isDup(),
                message.isRetain(),
                message.messageId(),
                message.properties()
        );
    }

    private Handler<MqttPublishMessage> handlePublish() {
        return message -> {
            if (checker.allowed()) {

            }
        };
    }

    private Handler<Integer> handlePublishAck() {
        return id -> {
            if (checker.allowed()) {

            }
        };
    }

    private Handler<Integer> handlePublishReceived() {
        return id -> {
            if (checker.allowed()) {

            }
        };
    }

    private Handler<Integer> handlePublishCompletionHandler() {
        return id -> {
            if (checker.allowed()) {

            }
        };
    }
}

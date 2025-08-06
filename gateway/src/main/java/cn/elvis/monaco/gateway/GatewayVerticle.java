package cn.elvis.monaco.gateway;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GatewayVerticle extends AbstractVerticle {

    private final Map<String, MqttEndpoint> clientStore = new ConcurrentHashMap<>();

    private final Map<String, List<MqttEndpoint>> subscribeStore = new ConcurrentHashMap<>();

    private final Map<Integer, MqttPublishMessage> messageStore = new ConcurrentHashMap<>();

    private final Map<Integer, Map<String, Boolean>> messageStateStore = new ConcurrentHashMap<>();

    @Override
    public void start() throws Exception {
        MqttServer server = MqttServer.create(vertx);
        server.endpointHandler(endpoint -> {
            endpoint.accept(true)
                    .publishAutoAck(true);
            clientStore.put(endpoint.clientIdentifier(), endpoint);
            endpoint.subscribeHandler(packet -> {
                for (MqttTopicSubscription topicSubscription : packet.topicSubscriptions()) {
                    var topicName = topicSubscription.topicName();
                    var clients = subscribeStore.getOrDefault(topicName, new CopyOnWriteArrayList<>());
                    clients.add(endpoint);
                    subscribeStore.put(topicName, clients);
                }
            });
            endpoint.publishHandler(packet -> {
                var topicName = packet.topicName();
                var qos = packet.qosLevel();
                var clients = subscribeStore.getOrDefault(topicName, new ArrayList<>());
                if (qos == MqttQoS.EXACTLY_ONCE) {
                    endpoint.publishReceived(packet.messageId());
                }
                for (MqttEndpoint client : clients) {
                    client.publish(topicName, packet.payload(), qos, packet.isDup(), packet.isRetain(), packet.messageId(), packet.properties());
                }
                if (qos == MqttQoS.EXACTLY_ONCE) {
                    var clientState = new ConcurrentHashMap<String, Boolean>();
                    for (MqttEndpoint client : clients) {
                        clientState.put(client.clientIdentifier(), false);
                    }
                    messageStateStore.put(packet.messageId(), clientState);
                    messageStore.put(packet.messageId(), packet);
                }
            });
            // 确认阶段1： PUBREC  发送端 --> 接收端
            // 确认阶段2： PUBCOMP 接收端 <-- 发送端
            endpoint.publishReleaseHandler(messageId -> {
                var messageState = messageStateStore.get(messageId);
                if (messageState == null || messageState.isEmpty()) {
                    return;
                }
                for (Map.Entry<String, Boolean> state : messageState.entrySet()) {
                    var client = clientStore.get(state.getKey());
                    if (Objects.nonNull(client)) {

                    }
                }
            });
        }).listen(18083);
    }
}

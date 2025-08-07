package cn.elvis.monaco.gateway;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The simplest MQTT Broker
 *  1. Always accept client connect request.
 *  2. Only subscribe exact topic(exclude wildcard topic & shared topic).
 *  3. Only publish to exact topic.
 *  4. Support qos2 messages.
 *     See also: <a href="https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_QoS_2:_Exactly">QoS 2: Exactly once delivery</a>
 *     ---------------------------------------------------------------------------------------------------
 *     | sender ---- PUBLISH ---> broker(counting total)   ---- PUBLISH ---> receiver_1, receiver2, .... |
 *     |        <--- PUBREC  ----                          <--- PUBREC  ----                             |
 *     |        ---- PUBREL  --->                          ---- PUBREL  --->                             |
 *     |        <--- PUBCOMP ---- (release stored message) <--- PUBCOMP ----                             |
 *     ---------------------------------------------------------------------------------------------------
 *  5. Not support bridge mode & cluster mode.
 *
 * @author qianwj
 * @since  v0.0.1
 */
public class GatewayVerticle extends AbstractVerticle {

    private final Map<String, MqttEndpoint> clientStore = new ConcurrentHashMap<>();

    private final Map<String, List<MqttEndpoint>> subscribeStore = new ConcurrentHashMap<>();

    private final Map<Integer, MqttPublishMessage> messageStore = new ConcurrentHashMap<>();

    /**
     * Stored published message state whether this message received.
     * key: message_id
     * value:
     *    key:   receiver_client_id
     *    value: true is received
     */
    static final Map<Integer, Map<String, Boolean>> messageStateStore = new ConcurrentHashMap<>();

    @Override
    public void start() throws Exception {
        final Logger log = LoggerFactory.getLogger(GatewayVerticle.class);
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
                log.info("client[" + endpoint.clientIdentifier() + "] receive PUBLISH packet: " + Json.encode(packet));
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
            endpoint.publishReceivedHandler(messageId -> {
                log.info("client[" + endpoint.clientIdentifier() + "] receive PUBREC packet: " + messageId);
                var receivedState = messageStateStore.getOrDefault(messageId, new ConcurrentHashMap<>());
                var received = receivedState.getOrDefault(endpoint.clientIdentifier(), false);
                if (received) {
                    endpoint.publishRelease(messageId);
                    return;
                }
                receivedState.put(endpoint.clientIdentifier(), true);
                endpoint.publishRelease(messageId);
                messageStateStore.put(messageId, receivedState);
            });
            endpoint.publishReleaseHandler(messageId -> {
                log.info("client[" + endpoint.clientIdentifier() + "] receive PUBREL packet: " + messageId);
                var messageState = messageStateStore.get(messageId);
                if (messageState == null || messageState.isEmpty()) {
                    endpoint.publishComplete(messageId);
                }
            });
            endpoint.publishCompletionHandler(messageId -> {
                log.info("client[" + endpoint.clientIdentifier() + "] receive PUBCOMP packet: " + messageId);
                var receiveState = messageStateStore.get(messageId);
                if (receiveState == null || receiveState.isEmpty()) {
                    messageStateStore.remove(messageId);
                    endpoint.publishComplete(messageId);
                    return;
                }
                receiveState.remove(endpoint.clientIdentifier());
            });
        }).listen(18083);
        log.info("broker started");
    }
}

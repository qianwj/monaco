package cn.elvis.monaco.session;

import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.entity.WillMessage;
import cn.elvis.monaco.manager.*;
import cn.elvis.monaco.metrics.Metrics;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.utils.MqttPropertiesUtils;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Handler;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The simplest MQTT Broker
 *  1. Not support server assigned client identifier.
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
public final class EndpointHandler implements Handler<MqttEndpoint> {

    private static final Logger log = LoggerFactory.getLogger(EndpointHandler.class);

    /**
     * Stored published message state whether this message received.
     * key: message_id
     * value:
     *    key:   receiver_client_id
     *    value: true is received
     */
    private static final Map<Integer, Map<String, Boolean>> messageStateStore = new ConcurrentHashMap<>();

    private final ClientSessionManager clientSessionManager;

    private final PublisherManager publisherManager;

    private final SubscriberManager subscriberManager;

    private final WillManager willManager;

    private final RetainMessageManager retainMessageManager;

    private final Settings settings;

    public EndpointHandler(ClientSessionManager clientSessionManager,
                           PublisherManager publisherManager,
                           SubscriberManager subscriberManager,
                           WillManager willManager,
                           RetainMessageManager retainMessageManager,
                           Settings settings) {
        this.clientSessionManager = clientSessionManager;
        this.publisherManager = publisherManager;
        this.subscriberManager = subscriberManager;
        this.willManager = willManager;
        this.retainMessageManager = retainMessageManager;
        this.settings = settings;
    }

    @Override
    public void handle(MqttEndpoint endpoint) {
        endpoint.autoKeepAlive(false);
        if (StringUtil.isNullOrEmpty(endpoint.clientIdentifier())) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }
        log.info("New client incoming: " + endpoint.clientIdentifier() + ", clean start: " + endpoint.isCleanSession());
        ClientSession session = new DefaultClientSession(endpoint, settings);
        MqttConnectReturnCode returnCode = clientSessionManager.register(session);
        if (returnCode != MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            endpoint.reject(returnCode);
            return;
        }
        Metrics.addClient();
        int topicAliasMaximum = MqttPropertiesUtils.intValue(endpoint.connectProperties(), MqttProperties.MqttPropertyType.TOPIC_ALIAS_MAXIMUM, settings.defaultReceiveMaximum());
        publisherManager.setTopicAliasMaximum(session.identifier(), topicAliasMaximum);
        log.info("Client session [" + endpoint.clientIdentifier() + "] connected. store will? " + endpoint.will().isWillFlag());
        if (endpoint.will().isWillFlag()) {
            WillMessage will = WillMessage.create(endpoint.will());
            willManager.addWill(session.identifier(), will);
        }
        endpoint.subscribeHandler(packet -> {
            log.info("client[" + endpoint.clientIdentifier() + "] receive SUBSCRIBE packet: " + packet);
            clientSessionManager.heartbeat(session.identifier());
            List<MqttSubAckReasonCode> reasonCodes = new ArrayList<>();
            for (MqttTopicSubscription mqttTopicSubscription : packet.topicSubscriptions()) {
                var subscription = Subscription.of(session, mqttTopicSubscription);
                var reasonCode = subscriberManager.subscribe(session, subscription);
                reasonCodes.add(reasonCode);
            }
            endpoint.subscribeAcknowledge(packet.messageId(), reasonCodes, packet.properties());
        });
        endpoint.unsubscribeHandler(packet -> {
            log.info("client[" + endpoint.clientIdentifier() + "] receive UNSUB packet: " + packet);
            clientSessionManager.heartbeat(session.identifier());
            var ackCodes = packet.topics()
                    .stream()
                    .map(topic -> subscriberManager.unsubscribe(session, topic))
                    .toList();
            endpoint.unsubscribeAcknowledge(packet.messageId(), ackCodes, packet.properties());
        });
        endpoint.publishHandler(packet -> {
            clientSessionManager.heartbeat(session.identifier());
            log.info("client[" + endpoint.clientIdentifier() + "] receive PUBLISH packet: " + Json.encode(packet));
            publisherManager.publish(endpoint, packet, subscriberManager::exists).map(message -> {
                if (message.retain()) {
                    retainMessageManager.addMessage(message);
                }
                return message;
            }).onFailure(ex -> {
                publisherManager.reject(endpoint, ex, packet);
                log.error("Failed to publish packet: ", ex);
            });
        });
        endpoint.publishReceivedHandler(messageId -> {
            clientSessionManager.heartbeat(session.identifier());
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
            clientSessionManager.heartbeat(session.identifier());
            log.info("client[" + endpoint.clientIdentifier() + "] receive PUBREL packet: " + messageId);
            var messageState = messageStateStore.get(messageId);
            if (messageState == null || messageState.isEmpty()) {
                endpoint.publishComplete(messageId);
            }
        });
        endpoint.publishCompletionHandler(messageId -> {
            clientSessionManager.heartbeat(session.identifier());
            log.info("client[" + endpoint.clientIdentifier() + "] receive PUBCOMP packet: " + messageId);
            var receiveState = messageStateStore.get(messageId);
            if (receiveState == null || receiveState.isEmpty()) {
                messageStateStore.remove(messageId);
                endpoint.publishComplete(messageId);
                return;
            }
            receiveState.remove(endpoint.clientIdentifier());
        });
        endpoint.pingHandler(v -> {
            clientSessionManager.heartbeat(session.identifier());
            endpoint.pong();
        });
        endpoint.closeHandler(v -> {
            clientSessionManager.unregister(session.identifier(), false);
            log.info("client[" + endpoint.clientIdentifier() + "] receive CLOSE packet.");
            Metrics.removeClient();
        });
        endpoint.disconnectMessageHandler(packet -> {
            log.info("client[" + endpoint.clientIdentifier() + "] receive DISCONNECT packet: " + packet);
            if (packet.code() == MqttDisconnectReasonCode.NORMAL) {
                clientSessionManager.unregister(session.identifier(), true);
                return;
            }
            clientSessionManager.unregister(session.identifier(), false);
            Metrics.removeClient();
        });
        endpoint.exceptionHandler(e -> {
            log.error("endpoint occur exception" + e.getLocalizedMessage());
            e.printStackTrace(System.err);
        });
    }
}

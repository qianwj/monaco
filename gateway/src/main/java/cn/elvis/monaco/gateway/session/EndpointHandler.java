package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.entity.PublishMessage;
import cn.elvis.monaco.gateway.entity.Subscription;
import cn.elvis.monaco.gateway.entity.WillMessage;
import cn.elvis.monaco.gateway.manager.ClientSessionManager;
import cn.elvis.monaco.gateway.manager.RetainMessageManager;
import cn.elvis.monaco.gateway.manager.SubscriberManager;
import cn.elvis.monaco.gateway.manager.WillManager;
import cn.elvis.monaco.gateway.settings.EnvironmentSettings;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
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
 * Endpoint handler, handle endpoint instance events.
 *
 * @author qianwj
 * @since  0.0.1
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

    private final SubscriberManager subscriberManager;

    private final WillManager willManager;

    private final RetainMessageManager retainMessageManager;

    public EndpointHandler(ClientSessionManager clientSessionManager,
                           SubscriberManager subscriberManager,
                           WillManager willManager,
                           RetainMessageManager retainMessageManager) {
        this.clientSessionManager = clientSessionManager;
        this.subscriberManager = subscriberManager;
        this.willManager = willManager;
        this.retainMessageManager = retainMessageManager;
    }

    @Override
    public void handle(MqttEndpoint endpoint) {
        endpoint.autoKeepAlive(false);
        if (StringUtil.isNullOrEmpty(endpoint.clientIdentifier())) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }
        log.info("New client incoming: " + endpoint.clientIdentifier() + ", clean start: " + endpoint.isCleanSession());
        ClientSession session = new DefaultClientSession(endpoint, EnvironmentSettings.getInstance());
        MqttConnectReturnCode returnCode = clientSessionManager.register(session);
        if (returnCode != MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            endpoint.reject(returnCode);
            return;
        }
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
            PublishMessage message = PublishMessage.of(packet);
            if (packet.isRetain()) {
                retainMessageManager.addMessage(message);
                return;
            }
            var topicName = packet.topicName();
            var qos = packet.qosLevel();
            if (qos == MqttQoS.EXACTLY_ONCE) {
                endpoint.publishReceived(packet.messageId());
            }
            var qos2messageClientState = new ConcurrentHashMap<String, Boolean>();
            subscriberManager.search(topicName, subscription -> {
                subscription.subscriber().forward(message);
                if (qos == MqttQoS.EXACTLY_ONCE) {
                    qos2messageClientState.put(subscription.sessionId(), false);
                }
            });
            if (qos == MqttQoS.AT_LEAST_ONCE) {
                messageStateStore.put(packet.messageId(), qos2messageClientState);
            }
            if (qos == MqttQoS.AT_MOST_ONCE || qos == MqttQoS.AT_LEAST_ONCE) {
                endpoint.publishAcknowledge(packet.messageId());
            }
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
        });
        endpoint.disconnectMessageHandler(packet -> {
            log.info("client[" + endpoint.clientIdentifier() + "] receive DISCONNECT packet: " + packet);
            if (packet.code() == MqttDisconnectReasonCode.NORMAL) {
                clientSessionManager.unregister(session.identifier(), true);
                return;
            }
            clientSessionManager.unregister(session.identifier(), false);
        });
        endpoint.exceptionHandler(e -> {
            log.error("endpoint occur exception" + e.getLocalizedMessage());
            e.printStackTrace(System.err);
        });
    }
}

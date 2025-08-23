package cn.elvis.monaco.session;

import cn.elvis.monaco.entity.ack.ConnectAcknowledge;
import cn.elvis.monaco.entity.WillMessage;
import cn.elvis.monaco.manager.*;
import cn.elvis.monaco.metrics.Metrics;
import io.vertx.core.Handler;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;

import java.util.Optional;

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

    private final ClientSessionManager clientSessionManager;

    private final PacketIdentifierManager packetIdentifierManager;

    private final PublisherManager publisherManager;

    private final SubscriberManager subscriberManager;

    private final WillManager willManager;

    public EndpointHandler(ClientSessionManager clientSessionManager, PacketIdentifierManager packetIdentifierManager,
                           PublisherManager publisherManager,
                           SubscriberManager subscriberManager,
                           WillManager willManager) {
        this.clientSessionManager = clientSessionManager;
        this.packetIdentifierManager = packetIdentifierManager;
        this.publisherManager = publisherManager;
        this.subscriberManager = subscriberManager;
        this.willManager = willManager;
    }

    @Override
    public void handle(MqttEndpoint endpoint) {
        var session = connect(endpoint);
        if (session.isEmpty()) {
            return;
        }
        disconnect(endpoint);
        subscribe(endpoint);
        publish(endpoint);

        endpoint.pingHandler(v -> {
            clientSessionManager.heartbeat(endpoint.clientIdentifier());
            endpoint.pong();
        }).exceptionHandler(e -> {
            log.error("endpoint occur exception" + e.getLocalizedMessage());
            e.printStackTrace(System.err);
        });
    }

    private Optional<ClientSession> connect(MqttEndpoint endpoint) {
        ConnectAcknowledge ack = clientSessionManager.register(endpoint);
        if (ack.reject()) {
            return Optional.empty();
        }
        log.info("Client session [" + endpoint.clientIdentifier() + "] connected. Store will? " + endpoint.will().isWillFlag());
        if (endpoint.will().isWillFlag()) {
            WillMessage will = WillMessage.create(endpoint.will(), endpoint.clientIdentifier());
            willManager.addWill(endpoint.clientIdentifier(), will);
        }
        return clientSessionManager.get(endpoint.clientIdentifier());
    }

    private void disconnect(MqttEndpoint endpoint) {
        endpoint.closeHandler(v -> {
            clientSessionManager.unregister(endpoint.clientIdentifier(), false);
            log.info("client[" + endpoint.clientIdentifier() + "] receive CLOSE packet.");
            Metrics.removeClient();
        }).disconnectMessageHandler(packet -> {
            log.info("client[" + endpoint.clientIdentifier() + "] receive DISCONNECT packet: " + packet);
            if (packet.code() == MqttDisconnectReasonCode.NORMAL) {
                clientSessionManager.unregister(endpoint.clientIdentifier(), true);
                return;
            }
            clientSessionManager.unregister(endpoint.clientIdentifier(), false);
            Metrics.removeClient();
        });
    }

    private void subscribe(MqttEndpoint endpoint) {
        endpoint.subscribeHandler(packet -> {
            log.info("client[" + endpoint.clientIdentifier() + "] receive SUBSCRIBE packet: " + packet);
            clientSessionManager.heartbeat(endpoint.clientIdentifier());
            var ack = packetIdentifierManager.setUsingPacketId(endpoint.clientIdentifier(), packet)
                    .orElse(subscriberManager.subscribe(endpoint, packet));
            ack.send(endpoint);
            packetIdentifierManager.unsetUsingPacketId(endpoint.clientIdentifier(), packet.messageId());
        }).unsubscribeHandler(packet -> {
            log.info("client[" + endpoint.clientIdentifier() + "] receive UNSUB packet: " + packet);
            clientSessionManager.heartbeat(endpoint.clientIdentifier());
            var ack = packetIdentifierManager.setUsingPacketId(endpoint.clientIdentifier(), packet)
                    .orElse(subscriberManager.unsubscribe(endpoint, packet));
            ack.send(endpoint);
            packetIdentifierManager.unsetUsingPacketId(endpoint.clientIdentifier(), packet.messageId());
        });
    }

    private void publish(MqttEndpoint endpoint) {
        endpoint.publishHandler(packet -> {
            clientSessionManager.heartbeat(endpoint.clientIdentifier());
            log.info("client[" + endpoint.clientIdentifier() + "] receive PUBLISH packet: " + Json.encode(packet));
            var ack = packetIdentifierManager.setUsingPacketId(endpoint.clientIdentifier(), packet)
                    .orElse(publisherManager.publish(endpoint, packet));
            ack.send(endpoint);
        }).publishAcknowledgeHandler(packetId -> {
            clientSessionManager.heartbeat(endpoint.clientIdentifier());
            log.info("client[" + endpoint.clientIdentifier() + "] receive PUBACK packet: " + packetId);
            packetIdentifierManager.unsetUsingPacketId(endpoint.clientIdentifier(), packetId);
            clientSessionManager.get(endpoint.clientIdentifier())
                    .ifPresent(session -> session.ack(packetId));
        }).publishReceivedHandler(packetId -> {
            clientSessionManager.heartbeat(endpoint.clientIdentifier());
            log.info("client[" + endpoint.clientIdentifier() + "] receive PUBREC packet: " + packetId);
            publisherManager.publishReceived(endpoint, packetId)
                    .send(endpoint);
        }).publishReleaseHandler(packetId -> {
            clientSessionManager.heartbeat(endpoint.clientIdentifier());
            log.info("client[" + endpoint.clientIdentifier() + "] receive PUBREL packet: " + packetId);
            publisherManager.releasePublish(packetId).send(endpoint);
        }).publishCompletionHandler(messageId -> {
            clientSessionManager.heartbeat(endpoint.clientIdentifier());
            log.info("client[" + endpoint.clientIdentifier() + "] receive PUBCOMP packet: " + messageId);
            // todo: remove receivers message
            // todo: remove unacked message from client
        });
    }
}

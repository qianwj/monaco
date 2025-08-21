package cn.elvis.monaco.session;

import cn.elvis.monaco.entity.ack.*;
import cn.elvis.monaco.entity.ack.PublishExchangeAcknowledge.ReasonCode;
import cn.elvis.monaco.manager.PacketIdentifierManager;
import cn.elvis.monaco.utils.Lists;
import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.vertx.mqtt.messages.*;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * todo: listen client close event and clean client packet id store
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultPacketIdentifierManager implements PacketIdentifierManager {

    private final Map<String, Set<Integer>> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Acknowledge> setUsingPacketId(String clientId, MqttMessage packet) {
        var packetId = packet.messageId();
        var packetIds = store.getOrDefault(clientId, new HashSet<>());
        if (packetIds.contains(packetId)) {
            Acknowledge ack = switch (packet) {
                case MqttPublishMessage ignored -> new PublishAcknowledge(packetId, ReasonCode.PACKET_IDENTIFIER_IN_USE, MqttPropertiesBuilder.create());
                case MqttSubscribeMessage s -> new SubscribeAcknowledge(packetId, Lists.repeat(MqttSubAckReasonCode.PACKET_IDENTIFIER_IN_USE, s.topicSubscriptions().size()), MqttPropertiesBuilder.create());
                case MqttUnsubscribeMessage s -> new UnsubscribeAcknowledge(packetId, Lists.repeat(MqttUnsubAckReasonCode.PACKET_IDENTIFIER_IN_USE, s.topics().size()), MqttPropertiesBuilder.create());
                default -> throw new IllegalArgumentException("Unsupported packet type: " + packet.getClass().getSimpleName());
            };
            return Optional.of(ack);
        }
        packetIds.add(packetId);
        store.put(clientId, packetIds);
        return Optional.empty();
    }

    @Override
    public void unsetUsingPacketId(String clientId, int packetId) {
        store.computeIfPresent(clientId, (k, previous) -> {
            previous.remove(packetId);
            return previous;
        });
    }

    @Override
    public void close() {

    }
}

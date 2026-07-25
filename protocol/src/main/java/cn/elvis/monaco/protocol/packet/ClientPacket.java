package cn.elvis.monaco.protocol.packet;

import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.Payload;
import cn.elvis.monaco.protocol.property.*;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.List;

/**
 * All MQTT control packets sent by the client.
 */
public sealed interface ClientPacket {

    record Connect(
            String clientId,
            boolean cleanStart,
            int keepAlive,
            WillMessage will,
            String username,
            byte[] password,
            ConnectProperties properties
    ) implements ClientPacket {
    }

    record Publish(
            String topicName,
            QoS qos,
            boolean retain,
            boolean dup,
            int packetId,
            Payload payload,
            PublishProperties properties
    ) implements ClientPacket {
    }

    record PubAck(int packetId, ReasonCode reasonCode, AckProperties properties) implements ClientPacket {
    }

    record PubRec(int packetId, ReasonCode reasonCode, AckProperties properties) implements ClientPacket {
    }

    record PubRel(int packetId, ReasonCode reasonCode, AckProperties properties) implements ClientPacket {
    }

    record PubComp(int packetId, ReasonCode reasonCode, AckProperties properties) implements ClientPacket {
    }

    record Subscribe(
            int packetId,
            List<Subscription> subscriptions,
            SubscribeProperties properties
    ) implements ClientPacket {
    }

    record Unsubscribe(
            int packetId,
            List<String> topicFilters,
            AckProperties properties
    ) implements ClientPacket {
    }

    record PingReq() implements ClientPacket {
    }

    record Disconnect(ReasonCode reasonCode, DisconnectProperties properties) implements ClientPacket {
    }

    record Auth(ReasonCode reasonCode, AuthProperties properties) implements ClientPacket {
    }
}

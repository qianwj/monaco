package cn.elvis.monaco.protocol.packet;

import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.Payload;
import cn.elvis.monaco.protocol.property.*;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.List;

/**
 * All MQTT control packets sent by the server.
 */
public sealed interface ServerPacket {

    record ConnAck(
            boolean sessionPresent,
            ReasonCode reasonCode,
            ConnAckProperties properties
    ) implements ServerPacket {
    }

    record Publish(
            String topicName,
            QoS qos,
            boolean retain,
            boolean dup,
            int packetId,
            Payload payload,
            PublishProperties properties
    ) implements ServerPacket {
    }

    record PubAck(int packetId, ReasonCode reasonCode, AckProperties properties) implements ServerPacket {
    }

    record PubRec(int packetId, ReasonCode reasonCode, AckProperties properties) implements ServerPacket {
    }

    record PubRel(int packetId, ReasonCode reasonCode, AckProperties properties) implements ServerPacket {
    }

    record PubComp(int packetId, ReasonCode reasonCode, AckProperties properties) implements ServerPacket {
    }

    record SubAck(
            int packetId,
            List<ReasonCode> reasonCodes,
            AckProperties properties
    ) implements ServerPacket {
    }

    record UnsubAck(
            int packetId,
            List<ReasonCode> reasonCodes,
            AckProperties properties
    ) implements ServerPacket {
    }

    record PingResp() implements ServerPacket {
    }

    record Disconnect(ReasonCode reasonCode, DisconnectProperties properties) implements ServerPacket {
    }

    record Auth(ReasonCode reasonCode, AuthProperties properties) implements ServerPacket {
    }
}

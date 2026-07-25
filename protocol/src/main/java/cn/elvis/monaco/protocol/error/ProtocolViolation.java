package cn.elvis.monaco.protocol.error;

import cn.elvis.monaco.protocol.packet.PacketType;
import cn.elvis.monaco.protocol.reason.ReasonCode;

/**
 * Represents a protocol-level violation that should result in
 * a specific MQTT Reason Code response and optionally closing the connection.
 */
public record ProtocolViolation(
        ReasonCode reasonCode,
        PacketType responseType,
        boolean closeConnection,
        String message
) {

    public static ProtocolViolation malformedPacket(String message) {
        return new ProtocolViolation(ReasonCode.MALFORMED_PACKET, PacketType.DISCONNECT, true, message);
    }

    public static ProtocolViolation protocolError(String message) {
        return new ProtocolViolation(ReasonCode.PROTOCOL_ERROR, PacketType.DISCONNECT, true, message);
    }

    public static ProtocolViolation connectRefused(ReasonCode reasonCode, String message) {
        return new ProtocolViolation(reasonCode, PacketType.CONNACK, true, message);
    }
}

package cn.elvis.monaco.core.state;

import cn.elvis.monaco.protocol.model.QoS;

import java.time.Instant;

/**
 * Persistent inflight record — stored independently per (clientId, direction, packetId).
 */
public record InflightRecord(
        String clientId,
        Direction direction,
        int packetId,
        String messageId,
        QoS qos,
        InflightState state,
        boolean dup,
        int sendCount,
        Instant updatedAt
) {

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    public enum InflightState {
        PENDING_ACK,       // QoS 1: waiting PUBACK
        RECEIVED_QOS2,     // QoS 2 inbound: PUBREC sent, waiting PUBREL
        PENDING_REC,       // QoS 2 outbound: waiting PUBREC
        PENDING_COMP       // QoS 2: waiting PUBCOMP (after PUBREL)
    }
}

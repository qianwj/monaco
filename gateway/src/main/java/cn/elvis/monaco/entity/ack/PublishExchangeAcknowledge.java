package cn.elvis.monaco.entity.ack;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;

public sealed interface PublishExchangeAcknowledge
        extends Acknowledge
        permits PublishAcknowledge, PublishComplete, PublishReceived, PublishRelease {

    int packetId();

    ReasonCode reasonCode();

    MqttPropertiesBuilder properties();

    enum ReasonCode {
        SUCCESS((byte)0x0),
        NO_MATCHING_SUBSCRIBERS((byte)0x10),
        UNSPECIFIED_ERROR((byte)0x80),
        IMPLEMENTATION_SPECIFIC_ERROR((byte)0x83),
        NOT_AUTHORIZED((byte)0x87),
        TOPIC_NAME_INVALID((byte)0x90),
        PACKET_IDENTIFIER_IN_USE((byte)0x91),
        QUOTA_EXCEEDED((byte)0x97),
        PAYLOAD_FORMAT_INVALID((byte)0x99)
        ;

        ReasonCode(byte byteValue) {
            this.byteValue = byteValue;
        }

        private final byte byteValue;

        public byte value() {
            return byteValue;
        }
    }
}

package cn.elvis.monaco.protocol.packet;

import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.Payload;
import cn.elvis.monaco.protocol.property.WillProperties;

/**
 * Will message carried in CONNECT packet payload.
 */
public record WillMessage(
        String topic,
        Payload payload,
        QoS qos,
        boolean retain,
        WillProperties properties
) {
}

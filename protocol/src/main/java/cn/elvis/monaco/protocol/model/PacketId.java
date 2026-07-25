package cn.elvis.monaco.protocol.model;

/**
 * MQTT Packet Identifier. Range 1-65535.
 */
public record PacketId(int value) {

    public PacketId {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException("PacketId must be 1-65535, got: " + value);
        }
    }
}

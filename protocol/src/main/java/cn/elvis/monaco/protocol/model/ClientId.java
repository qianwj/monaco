package cn.elvis.monaco.protocol.model;

/**
 * MQTT Client Identifier. Length 0-65535 bytes, valid UTF-8.
 * An empty clientId is allowed (server assigns one).
 */
public record ClientId(String value) {

    public ClientId {
        if (value == null) {
            throw new IllegalArgumentException("ClientId must not be null");
        }
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535) {
            throw new IllegalArgumentException("ClientId exceeds 65535 bytes");
        }
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }
}

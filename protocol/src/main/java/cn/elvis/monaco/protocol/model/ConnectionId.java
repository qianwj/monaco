package cn.elvis.monaco.protocol.model;

import java.util.UUID;

/**
 * Identifies a physical network connection. Not persisted.
 */
public record ConnectionId(String value) {

    public ConnectionId {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("ConnectionId must not be null or empty");
        }
    }

    public static ConnectionId generate() {
        return new ConnectionId(UUID.randomUUID().toString());
    }
}

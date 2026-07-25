package cn.elvis.monaco.protocol.property;

import java.util.List;

/**
 * MQTT 5.0 User Property (key-value pair). Can appear multiple times, order preserved.
 */
public record UserProperty(String key, String value) {

    public UserProperty {
        if (key == null) {
            throw new IllegalArgumentException("User property key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("User property value must not be null");
        }
    }
}

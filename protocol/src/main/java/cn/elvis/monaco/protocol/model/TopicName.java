package cn.elvis.monaco.protocol.model;

/**
 * MQTT Topic Name. Must not contain wildcards (+ or #).
 * Length 1-65535 bytes, valid UTF-8, no U+0000.
 */
public record TopicName(String value) {

    public TopicName {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("TopicName must not be null or empty");
        }
    }

    public String[] levels() {
        return value.split("/", -1);
    }
}

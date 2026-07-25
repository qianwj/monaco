package cn.elvis.monaco.protocol.model;

/**
 * MQTT message payload with optional format indicator.
 */
public record Payload(byte[] data, FormatIndicator formatIndicator) {

    public enum FormatIndicator {
        UNSPECIFIED,
        UTF8
    }

    public Payload(byte[] data) {
        this(data, FormatIndicator.UNSPECIFIED);
    }

    public Payload {
        if (data == null) {
            data = new byte[0];
        }
    }

    public boolean isEmpty() {
        return data.length == 0;
    }

    public int size() {
        return data.length;
    }
}

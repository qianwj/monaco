package cn.elvis.monaco.protocol.model;

/**
 * MQTT Quality of Service levels.
 */
public enum QoS {

    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2);

    private final int value;

    QoS(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static QoS valueOf(int value) {
        return switch (value) {
            case 0 -> AT_MOST_ONCE;
            case 1 -> AT_LEAST_ONCE;
            case 2 -> EXACTLY_ONCE;
            default -> throw new IllegalArgumentException("Invalid QoS value: " + value);
        };
    }

    /**
     * Returns the minimum of two QoS levels (effective QoS).
     */
    public QoS min(QoS other) {
        return this.value <= other.value ? this : other;
    }
}

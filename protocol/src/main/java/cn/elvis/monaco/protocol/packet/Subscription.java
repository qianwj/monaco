package cn.elvis.monaco.protocol.packet;

/**
 * Subscription entry within a SUBSCRIBE packet.
 */
public record Subscription(
        String topicFilter,
        int maxQoS,
        boolean noLocal,
        boolean retainAsPublished,
        RetainHandling retainHandling
) {

    public enum RetainHandling {
        SEND_AT_SUBSCRIBE(0),
        SEND_IF_NEW_SUBSCRIPTION(1),
        DO_NOT_SEND(2);

        private final int value;

        RetainHandling(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static RetainHandling valueOf(int value) {
            return switch (value) {
                case 0 -> SEND_AT_SUBSCRIBE;
                case 1 -> SEND_IF_NEW_SUBSCRIPTION;
                case 2 -> DO_NOT_SEND;
                default -> throw new IllegalArgumentException("Invalid RetainHandling: " + value);
            };
        }
    }
}

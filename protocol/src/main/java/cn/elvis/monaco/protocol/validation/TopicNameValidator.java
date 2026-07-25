package cn.elvis.monaco.protocol.validation;

/**
 * Validates MQTT Topic Names (used in PUBLISH).
 * <p>
 * Rules:
 * - Must not be null or empty
 * - Must not contain U+0000
 * - Must not contain wildcard characters (+ or #)
 * - UTF-8 encoded length must be 1-65535 bytes
 */
public final class TopicNameValidator {

    private TopicNameValidator() {
    }

    public static boolean isValid(String topicName) {
        if (topicName == null || topicName.isEmpty()) {
            return false;
        }
        if (topicName.contains("\u0000")) {
            return false;
        }
        if (topicName.contains("+") || topicName.contains("#")) {
            return false;
        }
        int byteLength = topicName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return byteLength >= 1 && byteLength <= 65535;
    }
}

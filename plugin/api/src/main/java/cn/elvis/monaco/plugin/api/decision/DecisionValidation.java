package cn.elvis.monaco.plugin.api.decision;

import java.nio.charset.StandardCharsets;

final class DecisionValidation {

    private static final int MAX_PUBLIC_MESSAGE_BYTES = 1_024;

    private DecisionValidation() {
    }

    static String publicMessage(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Plugin public message must not contain U+0000");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_PUBLIC_MESSAGE_BYTES) {
            throw new IllegalArgumentException(
                    "Plugin public message exceeds " + MAX_PUBLIC_MESSAGE_BYTES + " UTF-8 bytes");
        }
        return value;
    }
}

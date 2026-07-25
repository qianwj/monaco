package cn.elvis.monaco.protocol.model;

import java.util.Optional;

/**
 * MQTT Topic Filter. May contain wildcards (+ and #).
 * Length 1-65535 bytes, valid UTF-8, no U+0000.
 */
public record TopicFilter(String value) {

    private static final String SHARE_PREFIX = "$share/";

    public TopicFilter {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("TopicFilter must not be null or empty");
        }
    }

    public boolean isWildcard() {
        return value.contains("+") || value.contains("#");
    }

    public boolean isShared() {
        return value.startsWith(SHARE_PREFIX);
    }

    /**
     * For shared subscriptions, returns the group name.
     */
    public Optional<String> shareGroup() {
        if (!isShared()) {
            return Optional.empty();
        }
        int firstSlash = SHARE_PREFIX.length() - 1; // index of '/' after $share
        int secondSlash = value.indexOf('/', firstSlash + 1);
        if (secondSlash == -1) {
            return Optional.empty();
        }
        return Optional.of(value.substring(firstSlash + 1, secondSlash));
    }

    /**
     * For shared subscriptions, returns the actual filter (without $share/group/ prefix).
     * For normal filters, returns the value as-is.
     */
    public String actualFilter() {
        if (!isShared()) {
            return value;
        }
        int firstSlash = SHARE_PREFIX.length() - 1;
        int secondSlash = value.indexOf('/', firstSlash + 1);
        if (secondSlash == -1) {
            return value;
        }
        return value.substring(secondSlash + 1);
    }

    public String[] levels() {
        return actualFilter().split("/", -1);
    }
}

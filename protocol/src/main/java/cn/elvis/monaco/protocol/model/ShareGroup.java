package cn.elvis.monaco.protocol.model;

/**
 * Shared subscription group name.
 */
public record ShareGroup(String value) {

    public ShareGroup {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("ShareGroup must not be null or empty");
        }
        if (value.contains("+") || value.contains("#")) {
            throw new IllegalArgumentException("ShareGroup must not contain wildcards: " + value);
        }
        if (value.contains("/")) {
            throw new IllegalArgumentException("ShareGroup must not contain '/': " + value);
        }
    }
}

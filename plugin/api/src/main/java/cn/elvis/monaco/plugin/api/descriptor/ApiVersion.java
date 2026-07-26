package cn.elvis.monaco.plugin.api.descriptor;

import java.util.Objects;

/**
 * Version of the Monaco plugin contract.
 *
 * <p>The major component changes for incompatible API revisions. The minor
 * component only grows for backwards-compatible additions.</p>
 */
public record ApiVersion(int major, int minor) implements Comparable<ApiVersion> {

    public static final ApiVersion CURRENT = new ApiVersion(1, 0);

    public ApiVersion {
        if (major < 1) {
            throw new IllegalArgumentException("API major version must be positive");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("API minor version must not be negative");
        }
    }

    /**
     * Parses either a major-only value such as {@code 1} or a full value such
     * as {@code 1.2}.
     */
    public static ApiVersion parse(String value) {
        Objects.requireNonNull(value, "value");
        String[] components = value.trim().split("\\.", -1);
        if (components.length < 1 || components.length > 2) {
            throw new IllegalArgumentException("Invalid plugin API version: " + value);
        }
        try {
            int parsedMajor = Integer.parseInt(components[0]);
            int parsedMinor = components.length == 2 ? Integer.parseInt(components[1]) : 0;
            return new ApiVersion(parsedMajor, parsedMinor);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid plugin API version: " + value, exception);
        }
    }

    /** Returns whether this runtime version can load a plugin requiring {@code required}. */
    public boolean supports(ApiVersion required) {
        Objects.requireNonNull(required, "required");
        return major == required.major && minor >= required.minor;
    }

    @Override
    public int compareTo(ApiVersion other) {
        int majorOrder = Integer.compare(major, other.major);
        return majorOrder != 0 ? majorOrder : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}

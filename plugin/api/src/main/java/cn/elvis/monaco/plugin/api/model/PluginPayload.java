package cn.elvis.monaco.plugin.api.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Immutable, redacted payload value exposed to plugin hooks. */
public final class PluginPayload {

    public enum Format {
        UNSPECIFIED,
        UTF8
    }

    private static final PluginPayload EMPTY = new PluginPayload(new byte[0], Format.UNSPECIFIED);

    private final byte[] bytes;
    private final Format format;

    private PluginPayload(byte[] bytes, Format format) {
        this.bytes = bytes.clone();
        this.format = format;
    }

    public static PluginPayload empty() {
        return EMPTY;
    }

    public static PluginPayload of(byte[] bytes) {
        return of(bytes, Format.UNSPECIFIED);
    }

    public static PluginPayload of(byte[] bytes, Format format) {
        if (format == null) {
            throw new IllegalArgumentException("Plugin payload format must not be null");
        }
        if (bytes == null || bytes.length == 0) {
            return format == Format.UNSPECIFIED
                    ? EMPTY
                    : new PluginPayload(new byte[0], format);
        }
        return new PluginPayload(bytes, format);
    }

    public byte[] copyBytes() {
        return bytes.clone();
    }

    public ByteBuffer asReadOnlyBuffer() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    public Format format() {
        return format;
    }

    public int size() {
        return bytes.length;
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PluginPayload payload
                && format == payload.format
                && Arrays.equals(bytes, payload.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + format.hashCode();
    }

    @Override
    public String toString() {
        return "PluginPayload[size=" + bytes.length + ", format=" + format + ']';
    }
}

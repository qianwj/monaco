package cn.elvis.monaco.plugin.api.model;

import java.nio.ByteBuffer;

/** Sensitive bytes whose string representation never exposes content. */
public final class PluginSecret {

    private static final PluginSecret EMPTY = new PluginSecret(new byte[0]);

    private final byte[] bytes;

    private PluginSecret(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public static PluginSecret empty() {
        return EMPTY;
    }

    public static PluginSecret of(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? EMPTY : new PluginSecret(bytes);
    }

    public byte[] copyBytes() {
        return bytes.clone();
    }

    public ByteBuffer asReadOnlyBuffer() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    public int size() {
        return bytes.length;
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public String toString() {
        return "PluginSecret[REDACTED,size=" + bytes.length + ']';
    }
}

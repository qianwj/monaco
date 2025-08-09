package cn.elvis.monaco.gateway.entity.codec;

import cn.elvis.monaco.gateway.entity.WillMessageImpl;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

import java.io.*;

/**
 * Will Message Codec, encode & decode will message that use in vert.x event bus.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class WillMessageCodec implements MessageCodec<WillMessageImpl, WillMessageImpl> {

    @Override
    public void encodeToWire(Buffer buffer, WillMessageImpl willMessage) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(willMessage);
            out.flush();
            byte[] serialized = bos.toByteArray();
            buffer.appendInt(serialized.length);
            buffer.appendBytes(serialized);
        } catch (IOException e) {
            throw new IllegalStateException("encode will message error", e);
        }
    }

    @Override
    public WillMessageImpl decodeFromWire(int pos, Buffer buffer) {
        int _pos = pos;
        int length = buffer.getInt(_pos);
        // Jump 4 because getInt() == 4 bytes
        byte[] serialized = buffer.getBytes(_pos += 4, _pos + length);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serialized); ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (WillMessageImpl) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("decode will message error", e);
        }
    }

    @Override
    public WillMessageImpl transform(WillMessageImpl willMessage) {
        return willMessage;
    }

    @Override
    public String name() {
        return this.getClass().getCanonicalName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}

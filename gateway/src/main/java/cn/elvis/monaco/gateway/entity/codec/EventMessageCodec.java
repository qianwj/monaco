package cn.elvis.monaco.gateway.entity.codec;

import cn.elvis.monaco.gateway.entity.PublishMessageImpl;
import cn.elvis.monaco.gateway.entity.events.SubscriptionExtend;
import cn.elvis.monaco.gateway.entity.WillMessageImpl;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

import java.io.*;

/**
 * Event Message Codec, encode & decode event message that use in vert.x event bus.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class EventMessageCodec<T> implements MessageCodec<T, T> {

    private final Class<T> clazz;

    public static void register(Vertx vertx) {
        vertx.eventBus().registerDefaultCodec(WillMessageImpl.class, new EventMessageCodec<>(WillMessageImpl.class));
        vertx.eventBus().registerDefaultCodec(PublishMessageImpl.class, new EventMessageCodec<>(PublishMessageImpl.class));
        vertx.eventBus().registerDefaultCodec(SubscriptionExtend.class, new EventMessageCodec<>(SubscriptionExtend.class));
    }

    EventMessageCodec(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public void encodeToWire(Buffer buffer, T event) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(event);
            out.flush();
            byte[] serialized = bos.toByteArray();
            buffer.appendInt(serialized.length);
            buffer.appendBytes(serialized);
        } catch (IOException e) {
            throw new IllegalStateException("encode will message error", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T decodeFromWire(int pos, Buffer buffer) {
        int _pos = pos;
        int length = buffer.getInt(_pos);
        // Jump 4 because getInt() == 4 bytes
        byte[] serialized = buffer.getBytes(_pos += 4, _pos + length);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serialized); ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("decode will message error", e);
        }
    }

    @Override
    public T transform(T event) {
        return event;
    }

    @Override
    public String name() {
        return clazz.getCanonicalName() + "Codec";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}

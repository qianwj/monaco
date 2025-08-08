package cn.elvis.monaco.gateway.entity.codec;

import cn.elvis.monaco.gateway.entity.WillMessage;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

public class WillMessageCodec implements MessageCodec<WillMessage, WillMessage> {

    @Override
    public void encodeToWire(Buffer buffer, WillMessage willMessage) {
//        buffer.appendString(willMessage.delayInterval().toMillis() + "\n");
//        buffer.appendString(willMessage.expiryTime().toEpochMilli() + "\n");
//        buffer.appendBuffer(willMessage.body());
    }

    @Override
    public WillMessage decodeFromWire(int pos, Buffer buffer) {
        return null;
    }

    @Override
    public WillMessage transform(WillMessage willMessage) {
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

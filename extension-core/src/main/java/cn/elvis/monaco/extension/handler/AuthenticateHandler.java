package cn.elvis.monaco.extension.handler;

import cn.elvis.monaco.common.ChannelKeys;
import cn.elvis.monaco.extension.dsl.AuthenticateRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;

import java.nio.ByteBuffer;

final class AuthenticateHandler extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        vertx.eventBus().<Buffer>localConsumer(ChannelKeys.EXTENSION_AUTHENTICATE, message -> {
            var request = AuthenticateRequest.getRootAsAuthenticateRequest(ByteBuffer.wrap(message.body().getBytes()));

        });
    }
}

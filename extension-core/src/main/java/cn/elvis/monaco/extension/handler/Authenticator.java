package cn.elvis.monaco.extension.handler;

import cn.elvis.monaco.common.ChannelKeys;
import cn.elvis.monaco.extension.dsl.*;
import com.google.flatbuffers.FlatBufferBuilder;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

//final class Authenticator extends Handler {
//
//    public Authenticator(EventBus eventBus) {
//        super(eventBus);
//    }
//
//    @Override
//    public Future<ByteBuffer> apply(Request request) {
//        Objects.requireNonNull(request, "request must not be null");
//        if (request.requestType() == RequestType.AUTHENTICATE) {
//            var body = (AuthenticateRequest) request.requestBody(new AuthenticateRequest());
//            var options = new DeliveryOptions().setLocalOnly(true);
//            return eventBus.<Buffer>request(ChannelKeys.EXTENSION_AUTHENTICATE, body, options).compose(msg -> {
//                var reply = msg.body();
//                var builder = Optional.ofNullable(builderPool.get()).orElse(new FlatBufferBuilder());
//                var offset = Response.createResponse(
//                        builder,
//                        RequestType.AUTHENTICATE,
//                        RequestBody.authenticate,
//                        builder.createByteVector(reply.getBytes())
//                );
//                builder.finish(offset);
//                var result = builder.dataBuffer();
//                builder.clear();
//                builderPool.set(builder);
//                return Future.succeededFuture(result);
//            });
//        }
//        return Future.failedFuture(new IllegalAccessException("Invalid request type"));
//    }
//}

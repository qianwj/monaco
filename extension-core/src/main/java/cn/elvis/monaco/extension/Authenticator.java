package cn.elvis.monaco.extension;

import cn.elvis.monaco.common.ChannelKeys;
import cn.elvis.monaco.common.entity.AuthenticateResult;
import cn.elvis.monaco.extension.dsl.*;
import com.google.flatbuffers.FlatBufferBuilder;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

import java.nio.ByteBuffer;
import java.util.Optional;

public class Authenticator {

//    private final ThreadLocal<FlatBufferBuilder> builderPool = new ThreadLocal<>();
//
//    private final ExtensionManager manager;
//
//    public Authenticator(ExtensionManager manager) {
//        this.manager = manager;
//    }
//
//    public Future<AuthenticateResult> authenticate(String clientId, String username, String password) {
//        var builder = Optional.ofNullable(builderPool.get()).orElse(new FlatBufferBuilder());
//        var offset = AuthenticateRequest.createAuthenticateRequest(
//                builder,
//                builder.createString(username),
//                builder.createString(password),
//                builder.createString(clientId)
//        );
//        builder.finish(offset);
//        var data = builder.dataBuffer();
//        builder.clear();
//        builderPool.set(builder);
//        var options = new DeliveryOptions().setLocalOnly(true);
//        return eventBus.<Buffer>request(ChannelKeys.EXTENSION_AUTHENTICATE, Buffer.buffer(data.array()), options)
//                .compose(buf -> {
//                    var resp = Response.getRootAsResponse(ByteBuffer.wrap(buf.body().getBytes()));
//                    var reply = (AuthenticateReply) resp.responseBody(new AuthenticateReply());
//                    if (reply == null) {
//                        return Future.failedFuture(new RuntimeException("response is null"));
//                    }
//                    return Future.succeededFuture(new AuthenticateResult(reply.passed(), reply.message()));
//                });
//    }
}

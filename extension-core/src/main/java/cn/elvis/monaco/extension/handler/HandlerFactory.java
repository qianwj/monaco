package cn.elvis.monaco.extension.handler;

import cn.elvis.monaco.extension.dsl.Request;
import cn.elvis.monaco.extension.dsl.RequestType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class HandlerFactory {

    private final Map<Byte, Handler> handlers = new HashMap<>();

    public HandlerFactory(Vertx vertx) {
        var eventBus = vertx.eventBus();
//        handlers.put(RequestType.AUTHENTICATE, new Authenticator(eventBus));
    }

//    public Future<Buffer> handle(Buffer request) {
//        return Optional.ofNullable(handlers.get(request.requestType()))
//                .map(h -> h.apply(request))
//                .orElse(Future.failedFuture(new IllegalAccessException("Handler not register")))
//                .compose(buf -> Future.succeededFuture(Buffer.buffer(buf.array())));
//    }
}

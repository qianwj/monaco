package cn.elvis.monaco.extension;

import cn.elvis.monaco.extension.dsl.Request;
import cn.elvis.monaco.extension.handler.HandlerFactory;
import io.vertx.core.net.NetSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;

final class ExtensionSession {

    private static final Logger log = LogManager.getLogger(ExtensionSession.class);

    private final NetSocket connection;

    private final HandlerFactory handlerFactory;

    public ExtensionSession(NetSocket connection,
                            HandlerFactory handlerFactory,
                            int bufferSize) {
        this.connection = connection;
        this.handlerFactory = handlerFactory;
        connection.setWriteQueueMaxSize(bufferSize)
                .exceptionHandler(ex -> log.error("Extension connection exception", ex))
                .drainHandler(v -> connection.resume())
                .closeHandler(v -> log.info("Extension connection closed"));
        connect();
    }

    private void connect() {
        connection.handler(buffer -> {
            var request = Request.getRootAsRequest(ByteBuffer.wrap(buffer.getBytes()));
            handlerFactory.handle(request).onComplete(ar -> {
                if (ar.succeeded()) {
                    connection.write(ar.result());
                } else {
                    log.error("Extension handle message error: ", ar.cause());
                }
            });
        });
    }
}

package cn.elvis.monaco.extension;

import cn.elvis.monaco.extension.dsl.Metadata;
import cn.elvis.monaco.extension.dsl.Request;
import cn.elvis.monaco.extension.dsl.Response;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

final class ExtensionSession {

    private static final Logger log = LogManager.getLogger(ExtensionSession.class);

    private static final int CONNECT_STATE_INIT = 0;

    private static final int CONNECT_STATE_READY = 1;

    private static final int CONNECT_STATE_FAILED = 2;

    private final AtomicInteger requestId = new AtomicInteger();

    private final Map<Integer, Promise<Response>> pendingRequests = new ConcurrentHashMap<>();

    private final Vertx vertx;

    private final NetSocket connection;

    private final long requestTimeout;

    private volatile int connectState = CONNECT_STATE_INIT;

    private String sessionId;

    public ExtensionSession(Vertx vertx,
                            NetSocket connection,
                            int bufferSize,
                            int requestTimeoutSeconds) {
        this.vertx = vertx;
        this.connection = connection;
        this.requestTimeout = 1000L * requestTimeoutSeconds;
        connection.setWriteQueueMaxSize(bufferSize)
                .exceptionHandler(ex -> log.error("Extension connection exception", ex))
                .closeHandler(v -> log.info("Extension connection closed"));
    }

    public Future<ConnectionMetadata> connect() {
        Promise<ConnectionMetadata> connecting = Promise.promise();
        vertx.setTimer(requestTimeout, id -> {
            if (connectState == CONNECT_STATE_INIT) {
                connectState = CONNECT_STATE_FAILED;
                connecting.fail(new TimeoutException("Extension connection timeout"));
                connection.close();
            }
        });
        connection.handler(buffer -> {
            if (connectState == CONNECT_STATE_INIT) {
                try {
                    var metadata = Metadata.getRootAsMetadata(ByteBuffer.wrap(buffer.getBytes()));
                    var length = metadata.extensionTypesLength();
                    String[] extensionTypes = new String[length];
                    for (int i = 0; i < length; i++) {
                        extensionTypes[i] = metadata.extensionTypes(i);
                    }
                    this.sessionId = metadata.sessionId();
                    connecting.complete(new ConnectionMetadata(sessionId, extensionTypes));
                    connectState = CONNECT_STATE_READY;
                } catch (Exception e) {
                    connecting.fail(e);
                    connectState = CONNECT_STATE_FAILED;
                    connection.close();
                }
                return;
            }
            var requestId = buffer.getInt(0);
            var response = Response.getRootAsResponse(ByteBuffer.wrap(buffer.getBytes(4, buffer.length())));
            Optional.ofNullable(pendingRequests.get(requestId))
                    .ifPresent(promise -> {
                        promise.complete(response);
                        pendingRequests.remove(requestId);
                    });
        });
        return connecting.future();
    }

    public Future<Response> request(Request request) {
        int requestId = this.requestId.incrementAndGet();
        Promise<Response> promise = Promise.promise();
        vertx.setTimer(requestTimeout, t -> {
            var pending = pendingRequests.get(requestId);
            if (pending != null) {
                promise.tryFail(new TimeoutException("Extension request timed out"));
            }
        });
        pendingRequests.put(requestId, promise);
        Buffer buffer = Buffer.buffer()
                .appendInt(requestId)
                .appendBytes(request.getByteBuffer().array());
        connection.write(buffer).onFailure(ex -> {
            pendingRequests.remove(requestId);
            promise.fail(ex);
        });
        return promise.future();
    }

    public void close() {
        connection.close();
    }

    record ConnectionMetadata(String sessionId, String[] extensionTypes) {}
}

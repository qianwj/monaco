package cn.elvis.monaco.extension;

import cn.elvis.monaco.extension.handler.HandlerFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.SocketAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ExtensionServer extends AbstractVerticle {

    private static final Logger log = LogManager.getLogger(ExtensionServer.class);

    private final List<ExtensionSession> sessions = new ArrayList<>();

    private final SocketAddress address;

    public ExtensionServer(String path) {
        this.address = SocketAddress.domainSocketAddress(path);
    }

    @Override
    public void start() throws Exception {
        int bufferSize = config().getInteger("bufferSize", 100);
        HandlerFactory handlerFactory = new HandlerFactory(vertx);
        NetServer server = vertx.createNetServer();
        server.connectHandler(connection -> {
            var session = new ExtensionSession(connection, handlerFactory, bufferSize);
            sessions.add(session);
        }).listen(address).onComplete(ar -> {
           if (ar.succeeded()) {
               log.info("Extension server started on port {}", ar.result().actualPort());
           } else {
               log.error("Extension server start failed", ar.cause());
           }
        });
    }
}

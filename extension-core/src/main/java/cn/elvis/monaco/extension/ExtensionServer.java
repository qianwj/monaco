package cn.elvis.monaco.extension;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.SocketAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExtensionServer extends AbstractVerticle {

    private static final Logger log = LogManager.getLogger(ExtensionServer.class);

    private final Map<String, ExtensionSession> extensions = new ConcurrentHashMap<>();

    private final SocketAddress address;

    public ExtensionServer(String path) {
        this.address = SocketAddress.domainSocketAddress(path);
    }

    @Override
    public void start() throws Exception {
        int bufferSize = config().getInteger("bufferSize", 100);
        int requestTimeout = config().getInteger("requestTimeout", 1000);
        NetServer server = vertx.createNetServer();
        server.connectHandler(connection -> {
            var session = new ExtensionSession(vertx, connection, bufferSize, requestTimeout);
            session.connect().compose(metadata -> {
                boolean register = false;
                for (String extensionType : metadata.extensionTypes()) {
                    if (!extensions.containsKey(extensionType)) {
                        extensions.put(extensionType, session);
                        register = true;
                    }
                }
                if (!register) {
                    session.close();
                }
                return null;
            });
        }).listen(address).onComplete(ar -> {
           if (ar.succeeded()) {
               log.info("Extension server started on port {}", ar.result().actualPort());
           } else {
               log.error("Extension server start failed", ar.cause());
           }
        });
    }

    @Override
    public void stop(Promise<Void> stopper) throws Exception {
        extensions.values().forEach(ExtensionSession::close);
        stopper.complete();
    }
}

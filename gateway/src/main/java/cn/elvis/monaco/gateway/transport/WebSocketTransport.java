package cn.elvis.monaco.gateway.transport;

import cn.elvis.monaco.gateway.session.EndpointHandler;
import cn.elvis.monaco.gateway.settings.Settings;
import io.vertx.core.Promise;

public final class WebSocketTransport extends Transport {

    public WebSocketTransport(Settings settings, EndpointHandler endpointHandler) {
        super(settings, endpointHandler);
    }

    @Override
    public void start(Promise<Void> starter) throws Exception {
        startServer("WS").onComplete(starter);
    }
}

package cn.elvis.monaco.transport;

import cn.elvis.monaco.session.EndpointHandler;
import cn.elvis.monaco.settings.Settings;

import io.vertx.core.Promise;

public final class WebSocketTransport extends Transport {

    public WebSocketTransport(Settings settings, EndpointHandler endpointHandler) {
        super(settings, endpointHandler);
    }

    @Override
    public void start(Promise<Void> starter) throws Exception {
        startServer(TransportType.WS).onComplete(starter);
    }
}

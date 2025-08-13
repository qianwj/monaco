package cn.elvis.monaco.gateway.transport;

import cn.elvis.monaco.gateway.session.EndpointHandler;
import cn.elvis.monaco.gateway.settings.Settings;

import io.vertx.core.Promise;

/**
 * TCP transport, used to accept connection from client use tcp protocol.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class TCPTransport extends Transport {

    public TCPTransport(Settings settings,
                        EndpointHandler endpointHandler) {
        super(settings, endpointHandler);
    }

    @Override
    public void start(Promise<Void> starter) throws Exception {
        startServer(TransportType.TCP).onComplete(starter);
    }
}

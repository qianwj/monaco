package cn.elvis.monaco.transport;

import cn.elvis.monaco.Module;
import cn.elvis.monaco.session.EndpointHandler;
import cn.elvis.monaco.settings.Settings;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public final class TransportModule extends Module {

    public TransportModule(Vertx vertx, Settings settings) {
        super(vertx, settings);
    }

    /**
     *
     * @param dependency: first dependency is Session Module
     */
    @Override
    public void init(Module... dependency) {
        var session = dependency[0];
        var handler = session.instance(EndpointHandler.class);
        if (settings.tcp().enable()) {
            vertx.deployVerticle(
                    () -> new TCPTransport(settings, handler),
                    new DeploymentOptions()
                            .setInstances(settings.tcp().instances())
            );
        }
        if (settings.webSocket().enable()) {
            vertx.deployVerticle(
                    () -> new WebSocketTransport(settings, handler),
                    new DeploymentOptions()
                            .setInstances(settings.webSocket().instances())
            );
        }
    }
}

package cn.elvis.monaco.gateway;

import cn.elvis.monaco.gateway.entity.codec.EventMessageCodec;
import cn.elvis.monaco.gateway.manager.*;
import cn.elvis.monaco.gateway.session.*;
import cn.elvis.monaco.gateway.settings.Settings;
import cn.elvis.monaco.gateway.transport.TCPTransport;
import cn.elvis.monaco.gateway.transport.WebSocketTransport;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;

public class MonacoServer {

    private final Vertx vertx;

    private final Settings settings;

    private final List<Manager> managers = new ArrayList<>();

    public MonacoServer(Settings settings) {
        this.settings = settings;
        this.vertx = Vertx.vertx();
    }

    public void start() {
        printSettings(settings);
        EventMessageCodec.register(vertx);
        init();
    }

    public void stop() {
        for (Manager manager : managers) {
            manager.close();
        }
        vertx.close();
    }

    private void init() {
        final ClientSessionManager clientSessionManager = new DefaultClientSessionManager(settings, vertx);
        final SubscriberManager subscriberManager = new DefaultSubscriberManager(vertx.eventBus());
        final WillManager willManager = new DefaultWillManager(vertx.eventBus());
        final RetainMessageManager retainMessageManager = new DefaultRetainMessageManager(settings, vertx.eventBus());
        managers.addAll(List.of(clientSessionManager, subscriberManager, willManager, retainMessageManager));
        final EndpointHandler handler = new EndpointHandler(
                clientSessionManager,
                subscriberManager,
                willManager,
                retainMessageManager
        );
        if (settings.tcpTransportConfig().enable()) {
            vertx.deployVerticle(
                    () -> new TCPTransport(settings, handler),
                    new DeploymentOptions().setInstances(1)
            );
        }
        if (settings.webSocketTransportConfig().enable()) {
            vertx.deployVerticle(
                    () -> new WebSocketTransport(settings, handler),
                    new DeploymentOptions().setInstances(1)
            );
        }
    }

    private static void printSettings(Settings settings) {
        System.out.println("Monaco settings:");
        System.out.println("   maximum_session_count: " + settings.maximumSessionCount());
        System.out.println("   default_session_expiry_interval: " + settings.defaultSessionExpiryInterval());
        System.out.println("   max_session_expiry_interval: " + settings.maxSessionExpiryInterval());
        System.out.println("   default_receive_maximum: " + settings.defaultReceiveMaximum());
        System.out.println("   topic_alias_maximum: " + settings.topicAliasMaximum());
        System.out.println("   retain_available: " + settings.retainAvailable());
        System.out.println("   tcp_transport_config: " + settings.tcpTransportConfig());
        System.out.println("   websocket_transport_config: " + settings.webSocketTransportConfig());
    }
}

package cn.elvis.monaco;

import cn.elvis.monaco.entity.codec.EventMessageCodec;
import cn.elvis.monaco.manager.*;
import cn.elvis.monaco.manager.standalone.*;
import cn.elvis.monaco.metrics.Metrics;
import cn.elvis.monaco.session.*;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.*;
import cn.elvis.monaco.store.memory.*;
import cn.elvis.monaco.transport.TCPTransport;
import cn.elvis.monaco.transport.WebSocketTransport;
import io.vertx.core.*;
import io.vertx.micrometer.MicrometerMetricsFactory;

/**
 * Monaco Server, a simple and lightweight mqtt broker.
 * - authenticator module
 * - settings module
 * - manager module
 * - store module
 * - transport module
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class MonacoServer {

    private final Vertx vertx;

    private final Settings settings;

    public MonacoServer(Settings settings) {
        Settings.validate(settings);
        this.settings = settings;
        this.vertx = createVertx();
    }

    public void start() {
        printSettings(settings);
        EventMessageCodec.register(vertx);
        init();
    }

    public void stop() {
        vertx.close();
    }

    private Vertx createVertx() {
        VertxBuilder builder = Vertx.builder();
        if (settings.metrics().enable()) {
            Metrics.init();
            builder.with(
                    new VertxOptions()
                            .setMetricsOptions(settings.metrics().options())
                    )
                    .withMetrics(new MicrometerMetricsFactory(Metrics.registry()));
        }
        return builder.build();
    }

    private void init() {
        final ClientSessionStore clientSessionStore = new MemoryClientSessionStore();
        final TopicAliasStore topicAliasStore = new MemoryTopicAliasStore();
        final SubscriptionStore subscriptionStore = new MemorySubscriptionStore();
        final RetainMessageStore retainMessageStore = new MemoryRetainMessageStore();
        final MessageStore messageStore = new MemoryMessageStore();
        final ClientSessionManager clientSessionManager = new DefaultClientSessionManager(settings, vertx, topicAliasStore, clientSessionStore, messageStore);
        final PacketIdentifierManager packetIdentifierManager = new DefaultPacketIdentifierManager();
        final PublisherManager publisherManager = new DefaultPublisherManager(settings, vertx, clientSessionStore, topicAliasStore, retainMessageStore);
        final SubscriberManager subscriberManager = new DefaultSubscriberManager(settings, vertx.eventBus(), subscriptionStore);
        final WillManager willManager = new DefaultWillManager(vertx.eventBus());
        final EndpointHandler handler = new EndpointHandler(
                clientSessionManager,
                packetIdentifierManager,
                publisherManager,
                subscriberManager,
                willManager
        );
        vertx.deployVerticle(
                () -> new PushService(subscriptionStore, clientSessionStore),
                new DeploymentOptions()
                        .setInstances(1)
                        .setThreadingModel(ThreadingModel.WORKER)
        );
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

    private static void printSettings(Settings settings) {
        System.out.println("Monaco settings:");
        System.out.println("   maximum_session_count: " + settings.maximumSessionCount());
        System.out.println("   default_session_expiry_interval: " + settings.defaultSessionExpiryInterval());
        System.out.println("   max_session_expiry_interval: " + settings.maxSessionExpiryInterval());
        System.out.println("   default_receive_maximum: " + settings.defaultReceiveMaximum());
        System.out.println("   topic_alias_maximum: " + settings.topicAliasMaximum());
        System.out.println("   retain_available: " + settings.retainAvailable());
        System.out.println("   maximum_quality_of_service: " + settings.maximumQualityOfService());
        System.out.println("   server_keepalive_interval_maximum: " + settings.serverKeepaliveIntervalMaximum());
        System.out.println("   tcp_transport_config: " + settings.tcp());
        System.out.println("   websocket_transport_config: " + settings.webSocket());
        System.out.println("   metrics: " + settings.metrics());
    }
}

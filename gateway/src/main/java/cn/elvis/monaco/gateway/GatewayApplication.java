package cn.elvis.monaco.gateway;

import cn.elvis.monaco.gateway.entity.PublishMessageImpl;
import cn.elvis.monaco.gateway.entity.SubscriptionExtend;
import cn.elvis.monaco.gateway.entity.SubscriptionImpl;
import cn.elvis.monaco.gateway.entity.WillMessageImpl;
import cn.elvis.monaco.gateway.entity.codec.EventMessageCodec;
import cn.elvis.monaco.gateway.session.*;
import cn.elvis.monaco.gateway.settings.EnvironmentSettings;
import cn.elvis.monaco.gateway.settings.Settings;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public class GatewayApplication {

    public static void main(String[] args) {
        final Vertx vertx = Vertx.vertx();
        printSettings(EnvironmentSettings.getInstance());
        // register event message codec
        EventMessageCodec.register(vertx);
        final ClientSessionManager clientSessionManager = new DefaultClientSessionManager(
                EnvironmentSettings.getInstance(),
                vertx
        );
        final SubscriberManager subscriberManager = new DefaultSubscriberManager(vertx.eventBus());
        final WillManager willManager = new DefaultWillManager(vertx.eventBus());
        final RetainMessageManager retainMessageManager = new DefaultRetainMessageManager(
                EnvironmentSettings.getInstance(),
                vertx.eventBus()
        );
        vertx.deployVerticle(
                () -> new GatewayVerticle(clientSessionManager, subscriberManager, willManager, retainMessageManager),
                new DeploymentOptions().setInstances(1)
        );
        vertx.deployVerticle(() -> new MetricsVerticle(clientSessionManager), new DeploymentOptions().setInstances(1));
    }

    private static void printSettings(Settings settings) {
        System.out.println("Monaco settings:");
        System.out.println("   maximum_session_count: " + settings.maximumSessionCount());
        System.out.println("   default_session_expiry_interval: " + settings.defaultSessionExpiryInterval());
        System.out.println("   max_session_expiry_interval: " + settings.maxSessionExpiryInterval());
        System.out.println("   default_receive_maximum: " + settings.defaultReceiveMaximum());
        System.out.println("   retain_available: " + settings.retainAvailable());
    }
}

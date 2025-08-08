package cn.elvis.monaco.gateway;

import cn.elvis.monaco.gateway.entity.WillMessage;
import cn.elvis.monaco.gateway.entity.codec.WillMessageCodec;
import cn.elvis.monaco.gateway.session.*;
import cn.elvis.monaco.gateway.settings.EnvironmentSettings;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public class GatewayApplication {

    public static void main(String[] args) {
        final Vertx vertx = Vertx.vertx();
        final ClientSessionManager clientSessionManager = new DefaultClientSessionManager(
                EnvironmentSettings.getInstance(),
                vertx
        );
        final SubscriberManager subscriberManager = new DefaultSubscriberManager(vertx.eventBus());
        final WillManager willManager = new DefaultWillManager(vertx.eventBus());
        vertx.eventBus().registerCodec(new WillMessageCodec());
        vertx.deployVerticle(() -> new GatewayVerticle(clientSessionManager, subscriberManager, willManager), new DeploymentOptions().setInstances(1));
        vertx.deployVerticle(() -> new MetricsVerticle(clientSessionManager), new DeploymentOptions().setInstances(1));
    }
}

package cn.elvis.monaco.gateway;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public class GatewayApplication {

    public static void main(String[] args) {
        var vertx = Vertx.vertx();
        vertx.deployVerticle(GatewayVerticle.class, new DeploymentOptions().setInstances(1));
    }
}

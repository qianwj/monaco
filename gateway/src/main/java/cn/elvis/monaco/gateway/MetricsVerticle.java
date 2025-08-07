package cn.elvis.monaco.gateway;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.Json;

public class MetricsVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        vertx.createHttpServer().requestHandler(request -> {
            if ("/metrics".equals(request.path())) {
                String body = Json.encode(GatewayVerticle.messageStateStore);
                request.response().send(body);
                request.end();
            }
        }).listen(19091);
    }
}

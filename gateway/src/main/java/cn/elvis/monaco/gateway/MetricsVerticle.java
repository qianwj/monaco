package cn.elvis.monaco.gateway;

import cn.elvis.monaco.gateway.session.ClientSessionManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class MetricsVerticle extends AbstractVerticle {

    private final ClientSessionManager clientSessionManager;

    public MetricsVerticle(ClientSessionManager clientSessionManager) {
        this.clientSessionManager = clientSessionManager;
    }

    @Override
    public void start() throws Exception {
        vertx.createHttpServer().requestHandler(request -> {
            if ("/metrics".equals(request.path())) {
                JsonObject metrics = new JsonObject();
                metrics.put("messageState", GatewayVerticle.messageStateStore);
                metrics.put("clients", clientSessionManager.sessionCount());
                String body = Json.encode(GatewayVerticle.messageStateStore);
                request.response().send(body);
                request.end();
            }
        }).listen(19091);
    }
}

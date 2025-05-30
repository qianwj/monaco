package cn.elvis.monaco;

import cn.elvis.monaco.configuration.ConfigurationLoader;
import cn.elvis.monaco.configuration.TransportConfiguration;
import cn.elvis.monaco.transport.MqttTransport;
import cn.elvis.monaco.transport.TransportType;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public final class MonacoServer {

    private static final Logger log = LogManager.getLogger(MonacoServer.class);

    public MonacoServer() {
        System.setProperty(
                "vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.Log4j2LogDelegateFactory"
        );
    }

    public void start() {
        Vertx vertx = Vertx.vertx();
        ConfigurationLoader.load(vertx).compose(config -> {
            log.info("MonacoServer starting...");
            return startMqttTransport(vertx, config);
        });
    }

    private Future<Void> startMqttTransport(Vertx vertx, JsonObject config) {
        JsonObject transportConfig = config.getJsonObject("transport");
        var futures = new ArrayList<Future<String>>();
        for (TransportType type : TransportType.values()) {
            var transportKey = type.name().toLowerCase();
            if (transportConfig.containsKey(transportKey)) {
                try {
                    var serverConfig = transportConfig.getJsonObject(transportKey)
                            .mapTo(TransportConfiguration.class).setType(type);
                    var deploymentOptions = new DeploymentOptions()
                            .setInstances(serverConfig.getInstances())
                            .setThreadingModel(ThreadingModel.VIRTUAL_THREAD);
                    var deployResult = vertx.deployVerticle(() -> new MqttTransport(serverConfig), deploymentOptions);
                    futures.add(deployResult);
                } catch (Exception e) {
                    log.error("Deploying transport server failed: ", e);
                    futures.add(Future.failedFuture(e));
                }
            }
        }
        return Future.<String>all(futures).compose(depIds -> {
            log.info("MonacoServer started.");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (Object id : depIds.list()) {
                    vertx.undeploy(id.toString());
                }
            }));
            return Future.succeededFuture();
        });
    }
}

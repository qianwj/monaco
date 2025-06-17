package cn.elvis.monaco;

import cn.elvis.monaco.configuration.ConfigurationLoader;
import cn.elvis.monaco.configuration.MetricsConfiguration;
import cn.elvis.monaco.configuration.TransportConfiguration;
import cn.elvis.monaco.extension.ExtensionServer;
import cn.elvis.monaco.logging.LoggerInitializer;
import cn.elvis.monaco.transport.MqttTransport;
import cn.elvis.monaco.transport.TransportType;
import io.vertx.core.*;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

public final class MonacoServer {

    private static final Logger log = LogManager.getLogger(MonacoServer.class);

    public MonacoServer() {
        System.setProperty(
                "vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.Log4j2LogDelegateFactory"
        );
    }

    public void start() {
        long currentTimeMillis = System.currentTimeMillis();
        var vertx = Vertx.vertx();
        var config = ConfigurationLoader.load(vertx);
        config.put("startTime", currentTimeMillis);
        vertx.close();
        LoggerInitializer.init(config);
        log.info("MonacoServer starting...");
        createVertxInstance(config).compose(instance -> {
            try {
                startExtensionServer(instance, config);
                startMqttTransport(instance, config);
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
            return Future.succeededFuture();
        }).onFailure(e -> log.error("MonacoServer start failed", e));
    }

    private Future<Vertx> createVertxInstance(JsonObject config) {
        String mode = Optional.ofNullable(config.getString("mode", null))
                .filter(String::isBlank)
                .orElse("standalone")
                .toLowerCase();
        VertxOptions vertxOptions = initMetrics(config);
        return switch (mode) {
            case "cluster" -> {
                var clusterManager = new InfinispanClusterManager();
                yield Vertx.builder()
                        .with(vertxOptions)
                        .withClusterManager(clusterManager).buildClustered();
            }
            case "bridge" ->
                // todo: build tcp event bridge.
                    Future.succeededFuture(Vertx.vertx(vertxOptions));
            default -> Future.succeededFuture(Vertx.vertx(vertxOptions));
        };
    }

    private void startExtensionServer(Vertx vertx, JsonObject config) {
        JsonObject extensionConfig = config.getJsonObject("extensions");
        if (Objects.isNull(extensionConfig)) {
            log.warn("Extensions configuration is empty");
            return;
        }
        String path = extensionConfig.getString("address", "./extensions/data.socks");
        var file = new File(path);
        if (!file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                log.error("Can't create extension address", e);
                return;
            }
        }
        var options = new DeploymentOptions().setInstances(1).setConfig(extensionConfig);
        vertx.deployVerticle(new ExtensionServer(path), options);
    }

    private void startMqttTransport(Vertx vertx, JsonObject config) {
        JsonObject transportConfig = config.getJsonObject("transport");
        long startTime = config.getLong("startTime", System.currentTimeMillis());
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
        Future.<String>all(futures).onComplete(depIds -> {
            log.info("MonacoServer started. Time used: {}ms", System.currentTimeMillis() - startTime);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                vertx.close();
            }));
        });
    }

    private VertxOptions initMetrics(JsonObject config) {
        MetricsConfiguration metricsConfig = config.getJsonObject("metrics").mapTo(MetricsConfiguration.class);
        VertxOptions options = new VertxOptions();
        return options.setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setPrometheusOptions(
                                new VertxPrometheusOptions()
                                        .setEmbeddedServerEndpoint(metricsConfig.getEndpoint())
                                        .setEmbeddedServerOptions(new HttpServerOptions().setPort(metricsConfig.getPort()))
                                        .setStartEmbeddedServer(metricsConfig.isEnabled())
                                        .setEnabled(metricsConfig.isEnabled())
                        )
                        .setEnabled(metricsConfig.isEnabled())
                        .setJvmMetricsEnabled(true)
        );
    }
}

package cn.elvis.monaco.settings;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;

import java.util.StringJoiner;

record MetricsSettingsImpl(
        boolean enable,
        boolean exportJvmMetrics,
        String endpoint,
        int port) implements MetricsSettings {

    @Override
    public MicrometerMetricsOptions options() {
        return new MicrometerMetricsOptions()
                .setPrometheusOptions(
                        new VertxPrometheusOptions()
                                .setEnabled(enable)
                                .setStartEmbeddedServer(enable)
                                .setEmbeddedServerOptions(new HttpServerOptions().setPort(port))
                                .setEmbeddedServerEndpoint(endpoint)
                )
                .setEnabled(enable)
                .setNettyMetricsEnabled(enable)
                .setJvmMetricsEnabled(exportJvmMetrics);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "{", "}")
                .add("enable: " + enable)
                .add("exportJvmMetrics: " + exportJvmMetrics)
                .add("endpoint: '" + endpoint + "'")
                .add("port: " + port)
                .toString();
    }
}

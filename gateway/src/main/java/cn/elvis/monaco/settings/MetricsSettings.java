package cn.elvis.monaco.settings;

import io.vertx.micrometer.MicrometerMetricsOptions;

/**
 * Metrics Settings
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface MetricsSettings {

    boolean enable();

    boolean exportJvmMetrics();

    /**
     * Exposed metrics url endpoint
     */
    String endpoint();

    int port();

    MicrometerMetricsOptions options();
}

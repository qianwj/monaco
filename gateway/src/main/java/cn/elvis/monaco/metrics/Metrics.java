package cn.elvis.monaco.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics Tools
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class Metrics {

    private static boolean ENABLED = false;

    private static MeterRegistry REGISTRY;

    private static AtomicInteger CLIENT_COUNTER;

    public static void init() {
        ENABLED = true;
        REGISTRY = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(), Clock.SYSTEM);
        CLIENT_COUNTER = new AtomicInteger(0);
        REGISTRY.gauge("monaco_client_count", CLIENT_COUNTER, AtomicInteger::doubleValue);
    }

    public static void addClient() {
        if (ENABLED) {
            CLIENT_COUNTER.incrementAndGet();
        }
    }

    public static void removeClient() {
        if (ENABLED) {
            CLIENT_COUNTER.decrementAndGet();
        }
    }

    public static MeterRegistry registry() {
        return REGISTRY;
    }
}

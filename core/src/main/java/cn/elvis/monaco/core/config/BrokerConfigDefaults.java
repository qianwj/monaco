package cn.elvis.monaco.core.config;

import cn.elvis.monaco.protocol.model.QoS;

import java.time.Duration;

public final class BrokerConfigDefaults {

    private BrokerConfigDefaults() {}

    public static BrokerConfig defaults() {
        return new BrokerConfig(
                defaultTcp(),
                defaultWebSocket(),
                100_000,
                true,
                65535,
                Duration.ofMinutes(30),
                Duration.ofHours(24),
                65535,
                65535,
                500,
                268_435_456,
                1000,
                Duration.ZERO,
                QoS.EXACTLY_ONCE,
                true,
                true,
                true,
                true,
                "ALLOW_ANONYMOUS",
                "authentication.json",
                "data/rocksdb",
                defaultMetrics()
        );
    }

    public static TransportConfig defaultTcp() {
        return new TransportConfig(true, 1883, false, null, null, 1);
    }

    public static TransportConfig defaultWebSocket() {
        return new TransportConfig(false, 8083, false, null, null, 1);
    }

    public static MetricsConfig defaultMetrics() {
        return new MetricsConfig(false, 9095, "/metrics", false);
    }
}

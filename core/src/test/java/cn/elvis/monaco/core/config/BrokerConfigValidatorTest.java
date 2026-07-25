package cn.elvis.monaco.core.config;

import cn.elvis.monaco.protocol.model.QoS;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BrokerConfigValidatorTest {

    private BrokerConfig withOverride(java.util.function.Function<BrokerConfigBuilder, BrokerConfigBuilder> modifier) {
        return modifier.apply(new BrokerConfigBuilder()).build();
    }

    @Test
    void rejectsMaxPacketSizeZero() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b -> b.maxPacketSize(0))));
        assertTrue(ex.getMessage().contains("maxPacketSize"));
    }

    @Test
    void rejectsNullMaximumQoS() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b -> b.maximumQoS(null))));
        assertTrue(ex.getMessage().contains("maximumQoS"));
    }

    @Test
    void rejectsReceiveMaximumZero() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b -> b.defaultReceiveMaximum(0))));
        assertTrue(ex.getMessage().contains("defaultReceiveMaximum"));
    }

    @Test
    void rejectsMaxReceiveMaximumLessThanDefault() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b -> b.maxReceiveMaximum(5).defaultReceiveMaximum(10))));
        assertTrue(ex.getMessage().contains("maxReceiveMaximum must be >= defaultReceiveMaximum"));
    }

    @Test
    void rejectsSessionExpiryInversion() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b ->
                        b.defaultSessionExpiryInterval(Duration.ofHours(48))
                                .maxSessionExpiryInterval(Duration.ofHours(1)))));
        assertTrue(ex.getMessage().contains("maxSessionExpiryInterval"));
    }

    @Test
    void rejectsNegativeKeepAlive() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b -> b.serverKeepAlive(Duration.ofSeconds(-1)))));
        assertTrue(ex.getMessage().contains("serverKeepAlive"));
    }

    @Test
    void rejectsKeepAliveExceeding65535() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b -> b.serverKeepAlive(Duration.ofSeconds(70000)))));
        assertTrue(ex.getMessage().contains("serverKeepAlive"));
    }

    @Test
    void rejectsNoTransportEnabled() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b ->
                        b.tcp(new TransportConfig(false, 1883, false, null, null, 1))
                                .webSocket(new TransportConfig(false, 8083, false, null, null, 1)))));
        assertTrue(ex.getMessage().contains("at least one transport"));
    }

    @Test
    void rejectsTlsWithoutCert() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b ->
                        b.tcp(new TransportConfig(true, 8883, true, null, null, 1)))));
        assertTrue(ex.getMessage().contains("tlsCertPath"));
    }

    @Test
    void keepAliveTimeoutCalculation() {
        BrokerConfig config = withOverride(b -> b.serverKeepAlive(Duration.ofSeconds(60)));
        assertEquals(Duration.ofSeconds(90), config.keepAliveTimeout());
    }

    @Test
    void collectsMultipleErrors() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerConfigValidator.validate(withOverride(b ->
                        b.maxPacketSize(0).maximumQoS(null).defaultReceiveMaximum(0))));
        String msg = ex.getMessage();
        assertTrue(msg.contains("maxPacketSize"));
        assertTrue(msg.contains("maximumQoS"));
        assertTrue(msg.contains("defaultReceiveMaximum"));
    }

    /** Helper builder to create modified configs from defaults. */
    private static class BrokerConfigBuilder {
        private TransportConfig tcp = BrokerConfigDefaults.defaultTcp();
        private TransportConfig webSocket = BrokerConfigDefaults.defaultWebSocket();
        private int maxConnections = 100_000;
        private boolean serverAssignedClientIdentifier = true;
        private int maxClientIdentifierLength = 65535;
        private Duration defaultSessionExpiryInterval = Duration.ofMinutes(30);
        private Duration maxSessionExpiryInterval = Duration.ofHours(24);
        private int defaultReceiveMaximum = 65535;
        private int maxReceiveMaximum = 65535;
        private int topicAliasMaximum = 500;
        private int maxPacketSize = 268_435_456;
        private int maxQueueSize = 1000;
        private Duration serverKeepAlive = Duration.ZERO;
        private QoS maximumQoS = QoS.EXACTLY_ONCE;
        private boolean retainAvailable = true;
        private boolean wildcardSubscriptionAvailable = true;
        private boolean subscriptionIdentifierAvailable = true;
        private boolean sharedSubscriptionAvailable = true;
        private String authMode = "ALLOW_ANONYMOUS";
        private String authFilePath = "authentication.json";
        private String rocksdbPath = "data/rocksdb";
        private MetricsConfig metrics = BrokerConfigDefaults.defaultMetrics();

        BrokerConfigBuilder tcp(TransportConfig v) { this.tcp = v; return this; }
        BrokerConfigBuilder webSocket(TransportConfig v) { this.webSocket = v; return this; }
        BrokerConfigBuilder maxPacketSize(int v) { this.maxPacketSize = v; return this; }
        BrokerConfigBuilder maximumQoS(QoS v) { this.maximumQoS = v; return this; }
        BrokerConfigBuilder defaultReceiveMaximum(int v) { this.defaultReceiveMaximum = v; return this; }
        BrokerConfigBuilder maxReceiveMaximum(int v) { this.maxReceiveMaximum = v; return this; }
        BrokerConfigBuilder defaultSessionExpiryInterval(Duration v) { this.defaultSessionExpiryInterval = v; return this; }
        BrokerConfigBuilder maxSessionExpiryInterval(Duration v) { this.maxSessionExpiryInterval = v; return this; }
        BrokerConfigBuilder serverKeepAlive(Duration v) { this.serverKeepAlive = v; return this; }

        BrokerConfig build() {
            return new BrokerConfig(tcp, webSocket, maxConnections, serverAssignedClientIdentifier,
                    maxClientIdentifierLength, defaultSessionExpiryInterval, maxSessionExpiryInterval,
                    defaultReceiveMaximum, maxReceiveMaximum, topicAliasMaximum, maxPacketSize,
                    maxQueueSize, serverKeepAlive, maximumQoS, retainAvailable,
                    wildcardSubscriptionAvailable, subscriptionIdentifierAvailable,
                    sharedSubscriptionAvailable, authMode, authFilePath, rocksdbPath, metrics);
        }
    }
}

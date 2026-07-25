package cn.elvis.monaco.core.config;

import cn.elvis.monaco.protocol.model.QoS;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BrokerConfigDefaultsTest {

    @Test
    void defaultsPassValidation() {
        BrokerConfig config = BrokerConfigDefaults.defaults();
        assertDoesNotThrow(() -> BrokerConfigValidator.validate(config));
    }

    @Test
    void defaultTcpEnabled() {
        TransportConfig tcp = BrokerConfigDefaults.defaultTcp();
        assertTrue(tcp.enabled());
        assertEquals(1883, tcp.port());
        assertFalse(tcp.tlsEnabled());
    }

    @Test
    void defaultWebSocketDisabled() {
        TransportConfig ws = BrokerConfigDefaults.defaultWebSocket();
        assertFalse(ws.enabled());
        assertEquals(8083, ws.port());
    }

    @Test
    void defaultSessionExpiry() {
        BrokerConfig config = BrokerConfigDefaults.defaults();
        assertEquals(Duration.ofMinutes(30), config.defaultSessionExpiryInterval());
        assertEquals(Duration.ofHours(24), config.maxSessionExpiryInterval());
    }

    @Test
    void defaultMaximumQoS() {
        assertEquals(QoS.EXACTLY_ONCE, BrokerConfigDefaults.defaults().maximumQoS());
    }

    @Test
    void defaultCapabilitiesEnabled() {
        BrokerConfig config = BrokerConfigDefaults.defaults();
        assertTrue(config.retainAvailable());
        assertTrue(config.wildcardSubscriptionAvailable());
        assertTrue(config.subscriptionIdentifierAvailable());
        assertTrue(config.sharedSubscriptionAvailable());
    }

    @Test
    void keepAliveTimeoutZeroWhenNotForced() {
        BrokerConfig config = BrokerConfigDefaults.defaults();
        assertEquals(Duration.ZERO, config.serverKeepAlive());
        assertEquals(Duration.ZERO, config.keepAliveTimeout());
    }
}

package cn.elvis.monaco.core.config;

import cn.elvis.monaco.protocol.model.QoS;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class BrokerConfigLoaderTest {

    @Test
    void loadsDefaults() {
        BrokerConfig config = BrokerConfigLoader.load(key -> null);
        BrokerConfig defaults = BrokerConfigDefaults.defaults();

        assertEquals(defaults.maxConnections(), config.maxConnections());
        assertEquals(defaults.maximumQoS(), config.maximumQoS());
        assertEquals(defaults.defaultSessionExpiryInterval(), config.defaultSessionExpiryInterval());
        assertEquals(defaults.tcp().port(), config.tcp().port());
        assertTrue(config.tcp().enabled());
        assertFalse(config.webSocket().enabled());
    }

    @Test
    void overridesFromSource() {
        Map<String, String> env = new HashMap<>();
        env.put("MONACO_MAX_CONNECTIONS", "50000");
        env.put("MONACO_MAXIMUM_QOS", "1");
        env.put("MONACO_SERVER_KEEPALIVE", "60");
        env.put("MONACO_DEFAULT_SESSION_EXPIRY_INTERVAL", "3600");
        env.put("MONACO_RETAIN_AVAILABLE", "false");
        env.put("MONACO_TCP_PORT", "8883");
        env.put("MONACO_TCP_TLS_ENABLED", "true");
        env.put("MONACO_TCP_TLS_CERT_PATH", "/certs/server.crt");
        env.put("MONACO_TCP_TLS_KEY_PATH", "/certs/server.key");
        env.put("MONACO_METRICS_ENABLED", "true");
        env.put("MONACO_METRICS_PORT", "9100");

        BrokerConfig config = BrokerConfigLoader.load(env::get);

        assertEquals(50000, config.maxConnections());
        assertEquals(QoS.AT_LEAST_ONCE, config.maximumQoS());
        assertEquals(Duration.ofSeconds(60), config.serverKeepAlive());
        assertEquals(Duration.ofSeconds(3600), config.defaultSessionExpiryInterval());
        assertFalse(config.retainAvailable());
        assertEquals(8883, config.tcp().port());
        assertTrue(config.tcp().tlsEnabled());
        assertEquals("/certs/server.crt", config.tcp().tlsCertPath());
        assertEquals("/certs/server.key", config.tcp().tlsKeyPath());
        assertTrue(config.metrics().enabled());
        assertEquals(9100, config.metrics().port());
    }

    @Test
    void envOverridesProperties() {
        Map<String, String> env = new HashMap<>();
        env.put("MONACO_MAX_CONNECTIONS", "80000");
        env.put("MONACO_TCP_PORT", "1884");

        Properties props = new Properties();
        props.setProperty("monaco.max.connections", "50000");
        props.setProperty("monaco.tcp.port", "9999");
        props.setProperty("monaco.server.keepalive", "120");

        BrokerConfig config = BrokerConfigLoader.load(env::get, props);

        // env wins over properties
        assertEquals(80000, config.maxConnections());
        assertEquals(1884, config.tcp().port());
        // properties wins over defaults
        assertEquals(Duration.ofSeconds(120), config.serverKeepAlive());
    }

    @Test
    void propertiesOverridesDefaults() {
        Map<String, String> env = new HashMap<>(); // empty env

        Properties props = new Properties();
        props.setProperty("monaco.max.connections", "20000");
        props.setProperty("monaco.retain.available", "false");
        props.setProperty("monaco.metrics.enabled", "true");
        props.setProperty("monaco.metrics.port", "9200");

        BrokerConfig config = BrokerConfigLoader.load(env::get, props);

        assertEquals(20000, config.maxConnections());
        assertFalse(config.retainAvailable());
        assertTrue(config.metrics().enabled());
        assertEquals(9200, config.metrics().port());
    }

    @Test
    void blankValuesIgnored() {
        Map<String, String> env = new HashMap<>();
        env.put("MONACO_MAX_CONNECTIONS", "  ");
        env.put("MONACO_ROCKSDB_PATH", "");

        BrokerConfig config = BrokerConfigLoader.load(env::get);

        assertEquals(BrokerConfigDefaults.defaults().maxConnections(), config.maxConnections());
        assertEquals(BrokerConfigDefaults.defaults().rocksdbPath(), config.rocksdbPath());
    }

    @Test
    void blankEnvFallsToProperties() {
        Map<String, String> env = new HashMap<>();
        env.put("MONACO_MAX_CONNECTIONS", "  ");

        Properties props = new Properties();
        props.setProperty("monaco.max.connections", "30000");

        BrokerConfig config = BrokerConfigLoader.load(env::get, props);

        assertEquals(30000, config.maxConnections());
    }

    @Test
    void loadedConfigPassesValidation() {
        Map<String, String> env = new HashMap<>();
        env.put("MONACO_SERVER_KEEPALIVE", "120");
        env.put("MONACO_MAXIMUM_QOS", "0");

        BrokerConfig config = BrokerConfigLoader.load(env::get);
        assertDoesNotThrow(() -> BrokerConfigValidator.validate(config));
    }

    @Test
    void keepAliveTimeoutFromLoaded() {
        Map<String, String> env = new HashMap<>();
        env.put("MONACO_SERVER_KEEPALIVE", "100");

        BrokerConfig config = BrokerConfigLoader.load(env::get);
        assertEquals(Duration.ofSeconds(150), config.keepAliveTimeout());
    }

    @Test
    void webSocketOverride() {
        Map<String, String> env = new HashMap<>();
        env.put("MONACO_WS_ENABLED", "true");
        env.put("MONACO_WS_PORT", "9083");

        BrokerConfig config = BrokerConfigLoader.load(env::get);
        assertTrue(config.webSocket().enabled());
        assertEquals(9083, config.webSocket().port());
    }

    @Test
    void toPropertiesKeyConversion() {
        assertEquals("monaco.max.connections", BrokerConfigLoader.toPropertiesKey("MONACO_MAX_CONNECTIONS"));
        assertEquals("monaco.tcp.port", BrokerConfigLoader.toPropertiesKey("MONACO_TCP_PORT"));
        assertEquals("monaco.server.keepalive", BrokerConfigLoader.toPropertiesKey("MONACO_SERVER_KEEPALIVE"));
    }
}

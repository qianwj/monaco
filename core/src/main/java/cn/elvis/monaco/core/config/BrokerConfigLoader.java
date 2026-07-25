package cn.elvis.monaco.core.config;

import cn.elvis.monaco.protocol.model.QoS;

import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Loads BrokerConfig with layered priority: env > properties > defaults.
 *
 * <p>Resolution order for each key:
 * <ol>
 *   <li>Environment variables (MONACO_KEY_NAME)</li>
 *   <li>Properties file (monaco.key.name)</li>
 *   <li>Built-in defaults ({@link BrokerConfigDefaults})</li>
 * </ol>
 */
public final class BrokerConfigLoader {

    private static final String PREFIX = "MONACO_";

    // Session & connection keys
    static final String MAX_CONNECTIONS = PREFIX + "MAX_CONNECTIONS";
    static final String SERVER_ASSIGNED_CLIENT_IDENTIFIER = PREFIX + "SERVER_ASSIGNED_CLIENT_IDENTIFIER";
    static final String MAX_CLIENT_IDENTIFIER_LENGTH = PREFIX + "MAX_CLIENT_IDENTIFIER_LENGTH";
    static final String DEFAULT_SESSION_EXPIRY_INTERVAL = PREFIX + "DEFAULT_SESSION_EXPIRY_INTERVAL";
    static final String MAX_SESSION_EXPIRY_INTERVAL = PREFIX + "MAX_SESSION_EXPIRY_INTERVAL";
    static final String DEFAULT_RECEIVE_MAXIMUM = PREFIX + "DEFAULT_RECEIVE_MAXIMUM";
    static final String MAX_RECEIVE_MAXIMUM = PREFIX + "MAX_RECEIVE_MAXIMUM";

    // Protocol capability keys
    static final String TOPIC_ALIAS_MAXIMUM = PREFIX + "TOPIC_ALIAS_MAXIMUM";
    static final String MAX_PACKET_SIZE = PREFIX + "MAX_PACKET_SIZE";
    static final String MAX_QUEUE_SIZE = PREFIX + "MAX_QUEUE_SIZE";
    static final String SERVER_KEEPALIVE = PREFIX + "SERVER_KEEPALIVE";
    static final String MAXIMUM_QOS = PREFIX + "MAXIMUM_QOS";
    static final String RETAIN_AVAILABLE = PREFIX + "RETAIN_AVAILABLE";
    static final String WILDCARD_SUBSCRIPTION_AVAILABLE = PREFIX + "WILDCARD_SUBSCRIPTION_AVAILABLE";
    static final String SUBSCRIPTION_IDENTIFIER_AVAILABLE = PREFIX + "SUBSCRIPTION_IDENTIFIER_AVAILABLE";
    static final String SHARED_SUBSCRIPTION_AVAILABLE = PREFIX + "SHARED_SUBSCRIPTION_AVAILABLE";

    // Security keys
    static final String AUTH_MODE = PREFIX + "AUTH_MODE";
    static final String AUTH_FILE_PATH = PREFIX + "AUTH_FILE_PATH";

    // Store keys
    static final String ROCKSDB_PATH = PREFIX + "ROCKSDB_PATH";

    // Transport keys
    static final String TCP_PREFIX = PREFIX + "TCP_";
    static final String WS_PREFIX = PREFIX + "WS_";
    static final String TRANSPORT_ENABLED = "ENABLED";
    static final String TRANSPORT_PORT = "PORT";
    static final String TRANSPORT_TLS_ENABLED = "TLS_ENABLED";
    static final String TRANSPORT_TLS_CERT_PATH = "TLS_CERT_PATH";
    static final String TRANSPORT_TLS_KEY_PATH = "TLS_KEY_PATH";
    static final String TRANSPORT_INSTANCES = "INSTANCES";

    // Metrics keys
    static final String METRICS_ENABLED = PREFIX + "METRICS_ENABLED";
    static final String METRICS_EXPORT_JVM = PREFIX + "METRICS_EXPORT_JVM";
    static final String METRICS_ENDPOINT = PREFIX + "METRICS_ENDPOINT";
    static final String METRICS_PORT = PREFIX + "METRICS_PORT";

    private BrokerConfigLoader() {}

    /**
     * Load config with full priority chain: env > system properties > defaults.
     */

    public static BrokerConfig load() {
        return load(System::getenv, System.getProperties());
    }

    /**
     * Load config: env > properties > defaults.
     */
    public static BrokerConfig load(Function<String, String> env, Properties properties) {
        Function<String, String> source = layered(env, properties);
        return loadFrom(source);
    }

    /**
     * Load config from a single key-value source (fallback to defaults only).
     */
    public static BrokerConfig load(Function<String, String> source) {
        return loadFrom(source);
    }

    /**
     * Creates a layered source: env takes priority, then properties.
     * Environment key: MONACO_MAX_CONNECTIONS
     * Properties key: monaco.max.connections
     */
    private static Function<String, String> layered(Function<String, String> env, Properties properties) {
        return key -> {
            // Try env first (key as-is, e.g. MONACO_MAX_CONNECTIONS)
            String value = env.apply(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
            // Try properties (convert MONACO_MAX_CONNECTIONS -> monaco.max.connections)
            String propsKey = toPropertiesKey(key);
            value = properties.getProperty(propsKey);
            if (value != null && !value.isBlank()) {
                return value;
            }
            return null;
        };
    }

    /**
     * Convert env-style key to properties-style key.
     * MONACO_MAX_CONNECTIONS -> monaco.max.connections
     */
    static String toPropertiesKey(String envKey) {
        return envKey.toLowerCase().replace('_', '.');
    }

    private static BrokerConfig loadFrom(Function<String, String> source) {
        BrokerConfig defaults = BrokerConfigDefaults.defaults();

        return new BrokerConfig(
                loadTransport(source, TCP_PREFIX, defaults.tcp()),
                loadTransport(source, WS_PREFIX, defaults.webSocket()),
                intVal(source, MAX_CONNECTIONS, defaults.maxConnections()),
                boolVal(source, SERVER_ASSIGNED_CLIENT_IDENTIFIER, defaults.serverAssignedClientIdentifier()),
                intVal(source, MAX_CLIENT_IDENTIFIER_LENGTH, defaults.maxClientIdentifierLength()),
                durationSeconds(source, DEFAULT_SESSION_EXPIRY_INTERVAL, defaults.defaultSessionExpiryInterval()),
                durationSeconds(source, MAX_SESSION_EXPIRY_INTERVAL, defaults.maxSessionExpiryInterval()),
                intVal(source, DEFAULT_RECEIVE_MAXIMUM, defaults.defaultReceiveMaximum()),
                intVal(source, MAX_RECEIVE_MAXIMUM, defaults.maxReceiveMaximum()),
                intVal(source, TOPIC_ALIAS_MAXIMUM, defaults.topicAliasMaximum()),
                intVal(source, MAX_PACKET_SIZE, defaults.maxPacketSize()),
                intVal(source, MAX_QUEUE_SIZE, defaults.maxQueueSize()),
                durationSeconds(source, SERVER_KEEPALIVE, defaults.serverKeepAlive()),
                qosVal(source, MAXIMUM_QOS, defaults.maximumQoS()),
                boolVal(source, RETAIN_AVAILABLE, defaults.retainAvailable()),
                boolVal(source, WILDCARD_SUBSCRIPTION_AVAILABLE, defaults.wildcardSubscriptionAvailable()),
                boolVal(source, SUBSCRIPTION_IDENTIFIER_AVAILABLE, defaults.subscriptionIdentifierAvailable()),
                boolVal(source, SHARED_SUBSCRIPTION_AVAILABLE, defaults.sharedSubscriptionAvailable()),
                strVal(source, AUTH_MODE, defaults.authMode()),
                strVal(source, AUTH_FILE_PATH, defaults.authFilePath()),
                strVal(source, ROCKSDB_PATH, defaults.rocksdbPath()),
                loadMetrics(source, defaults.metrics())
        );
    }

    private static TransportConfig loadTransport(Function<String, String> source,
                                                  String prefix,
                                                  TransportConfig defaults) {
        return new TransportConfig(
                boolVal(source, prefix + TRANSPORT_ENABLED, defaults.enabled()),
                intVal(source, prefix + TRANSPORT_PORT, defaults.port()),
                boolVal(source, prefix + TRANSPORT_TLS_ENABLED, defaults.tlsEnabled()),
                strOrNull(source, prefix + TRANSPORT_TLS_CERT_PATH, defaults.tlsCertPath()),
                strOrNull(source, prefix + TRANSPORT_TLS_KEY_PATH, defaults.tlsKeyPath()),
                intVal(source, prefix + TRANSPORT_INSTANCES, defaults.instances())
        );
    }

    private static MetricsConfig loadMetrics(Function<String, String> source, MetricsConfig defaults) {
        return new MetricsConfig(
                boolVal(source, METRICS_ENABLED, defaults.enabled()),
                intVal(source, METRICS_PORT, defaults.port()),
                strVal(source, METRICS_ENDPOINT, defaults.endpoint()),
                boolVal(source, METRICS_EXPORT_JVM, defaults.exportJvmMetrics())
        );
    }

    private static int intVal(Function<String, String> source, String key, int defaultValue) {
        return resolve(source, key).map(Integer::parseInt).orElse(defaultValue);
    }

    private static boolean boolVal(Function<String, String> source, String key, boolean defaultValue) {
        return resolve(source, key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    private static String strVal(Function<String, String> source, String key, String defaultValue) {
        return resolve(source, key).orElse(defaultValue);
    }

    private static String strOrNull(Function<String, String> source, String key, String defaultValue) {
        return resolve(source, key).orElse(defaultValue);
    }

    private static Duration durationSeconds(Function<String, String> source, String key, Duration defaultValue) {
        return resolve(source, key).map(v -> Duration.ofSeconds(Long.parseLong(v))).orElse(defaultValue);
    }

    private static QoS qosVal(Function<String, String> source, String key, QoS defaultValue) {
        return resolve(source, key).map(v -> QoS.valueOf(Integer.parseInt(v))).orElse(defaultValue);
    }

    private static Optional<String> resolve(Function<String, String> source, String key) {
        String value = source.apply(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }
}

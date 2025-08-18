package cn.elvis.monaco.settings;

import cn.elvis.monaco.transport.TransportType;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Application settings from environment variables.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class EnvironmentSettings implements Settings {

    private static final String KEY_PREFIX = "MONACO_";

    static final String MAXIMUM_SESSION_COUNT_KEY = "MAXIMUM_SESSION_COUNT";

    static final String SERVER_ASSIGNED_CLIENT_IDENTIFIER_KEY = "SERVER_ASSIGNED_CLIENT_IDENTIFIER";

    static final String MAXIMUM_CLIENT_IDENTIFIER_LENGTH_KEY = "MAXIMUM_CLIENT_IDENTIFIER_LENGTH";

    static final String DEFAULT_SESSION_EXPIRY_INTERVAL_KEY = KEY_PREFIX + "DEFAULT_SESSION_EXPIRY_INTERVAL";

    static final String MAX_SESSION_EXPIRY_INTERVAL_KEY = KEY_PREFIX + "MAX_SESSION_EXPIRY_INTERVAL";

    static final String DEFAULT_RECEIVE_MAXIMUM_KEY = KEY_PREFIX + "DEFAULT_RECEIVE_MAXIMUM";

    static final String MAX_RECEIVE_MAXIMUM_KEY = KEY_PREFIX + "MAX_RECEIVE_MAXIMUM";

    static final String TOPIC_ALIAS_MAXIMUM_KEY = KEY_PREFIX + "TOPIC_ALIAS_MAXIMUM";

    static final String RETAIN_AVAILABLE_KEY = KEY_PREFIX + "RETAIN_AVAILABLE";

    static final String MAXIMUM_QOS_KEY = KEY_PREFIX + "MAXIMUM_QOS";

    static final String WILDCARD_SUBSCRIPTION_AVAILABLE_KEY = KEY_PREFIX + "WILDCARD_SUBSCRIPTION_AVAILABLE";

    static final String SUBSCRIPTION_IDENTIFIER_AVAILABLE_KEY = KEY_PREFIX + "SUBSCRIPTION_IDENTIFIER_AVAILABLE";

    static final String PUBLISH_QUEUE_MAXIMUM_KEY = KEY_PREFIX + "PUBLISH_QUEUE_MAXIMUM";

    static final String TCP_TRANSPORT_KEY_PREFIX = KEY_PREFIX + "TCP_TRANSPORT_";

    static final String WS_TRANSPORT_KEY_PREFIX = KEY_PREFIX + "WS_TRANSPORT_";

    static final String TRANSPORT_ENABLE_KEY = "ENABLE";

    static final String TRANSPORT_PORT_KEY = "PORT";

    static final String TRANSPORT_USE_TLS_KEY = "USE_TLS";

    static final String TRANSPORT_INSTANCES_KEY = "INSTANCES";

    static final String METRICS_ENABLED_KEY = KEY_PREFIX + "METRICS_ENABLE";

    static final String METRICS_EXPORT_JVM_METRICS_KEY = KEY_PREFIX + "METRICS_EXPORT_JVM_METRICS";

    static final String METRICS_PORT_KEY = KEY_PREFIX + "METRICS_PORT";

    static final String METRICS_ENDPOINT_KEY = KEY_PREFIX + "METRICS_ENDPOINT";

    private final Settings defaultSettings = DefaultSettings.getInstance();

    private final TransportSettings tcpTransportConfig;

    private final TransportSettings webSocketTransportConfig;

    private final MetricsSettings metricsConfig;

    private static final EnvironmentSettings INSTANCE = new EnvironmentSettings();

    private EnvironmentSettings() {
        this.tcpTransportConfig = getTransportSettings(
                TransportType.TCP,
                defaultSettings.tcp(),
                TCP_TRANSPORT_KEY_PREFIX
        );
        this.webSocketTransportConfig = getTransportSettings(
                TransportType.WS,
                defaultSettings.webSocket(),
                WS_TRANSPORT_KEY_PREFIX
        );
        this.metricsConfig = getMetricsSettings();
    }

    public static Settings getInstance() {
        return INSTANCE;
    }

    @Override
    public int maximumSessionCount() {
        return Settings.intValue(MAXIMUM_SESSION_COUNT_KEY, System::getenv, defaultSettings::maximumSessionCount);
    }

    @Override
    public boolean serverAssignedClientIdentifier() {
        return Settings.booleanValue(SERVER_ASSIGNED_CLIENT_IDENTIFIER_KEY, System::getenv, defaultSettings::serverAssignedClientIdentifier);
    }

    @Override
    public int maximumClientIdentifierLength() {
        return Settings.intValue(MAXIMUM_CLIENT_IDENTIFIER_LENGTH_KEY, System::getenv, defaultSettings::maximumClientIdentifierLength);
    }

    @Override
    public int defaultSessionExpiryInterval() {
        return Settings.intValue(DEFAULT_SESSION_EXPIRY_INTERVAL_KEY, System::getenv, defaultSettings::defaultSessionExpiryInterval);
    }

    @Override
    public int maxSessionExpiryInterval() {
        return Settings.intValue(MAX_SESSION_EXPIRY_INTERVAL_KEY, System::getenv, defaultSettings::maxSessionExpiryInterval);
    }

    @Override
    public int defaultReceiveMaximum() {
        return Settings.intValue(DEFAULT_RECEIVE_MAXIMUM_KEY, System::getenv, defaultSettings::defaultReceiveMaximum);
    }

    @Override
    public int maxReceiveMaximum() {
        return Settings.intValue(MAX_RECEIVE_MAXIMUM_KEY, System::getenv, defaultSettings::maxReceiveMaximum);
    }

    @Override
    public boolean retainAvailable() {
        return Settings.booleanValue(RETAIN_AVAILABLE_KEY, System::getenv, defaultSettings::retainAvailable);
    }

    @Override
    public int topicAliasMaximum() {
        return Settings.intValue(TOPIC_ALIAS_MAXIMUM_KEY, System::getenv, defaultSettings::topicAliasMaximum);
    }

    @Override
    public boolean wildcardSubscriptionAvailable() {
        return Settings.booleanValue(WILDCARD_SUBSCRIPTION_AVAILABLE_KEY, System::getenv, defaultSettings::wildcardSubscriptionAvailable);
    }

    @Override
    public int maximumQualityOfService() {
        return Settings.intValue(MAXIMUM_QOS_KEY, System::getenv, defaultSettings::maximumQualityOfService);
    }

    @Override
    public boolean subscriptionIdentifierAvailable() {
        return Settings.booleanValue(SUBSCRIPTION_IDENTIFIER_AVAILABLE_KEY, System::getenv, defaultSettings::subscriptionIdentifierAvailable);
    }

    @Override
    public int publishQueueMaximum() {
        return Settings.intValue(PUBLISH_QUEUE_MAXIMUM_KEY, System::getenv, defaultSettings::publishQueueMaximum);
    }

    @Override
    public TransportSettings tcp() {
        return tcpTransportConfig;
    }

    @Override
    public TransportSettings webSocket() {
        return webSocketTransportConfig;
    }

    @Override
    public MetricsSettings metrics() {
        return metricsConfig;
    }

    private TransportSettings getTransportSettings(TransportType transportType,
                                                   TransportSettings defaultSettings,
                                                   String prefix) {
        boolean enable = Settings.booleanValue(prefix + TRANSPORT_ENABLE_KEY, System::getenv, defaultSettings::enable);
        int port = Settings.intValue(prefix + TRANSPORT_PORT_KEY, System::getenv, defaultSettings::port);
        boolean useTLS = Settings.booleanValue(prefix + TRANSPORT_USE_TLS_KEY, System::getenv, defaultSettings::useTLS);
        int instances = Settings.intValue(prefix + TRANSPORT_INSTANCES_KEY, System::getenv, defaultSettings::instances);
        return new TransportSettings(transportType, enable, port, useTLS, instances);
    }

    private MetricsSettings getMetricsSettings() {
        boolean enable = Settings.booleanValue(METRICS_ENABLED_KEY, System::getenv, defaultSettings.metrics()::enable);
        boolean exportJvmMetrics = Settings.booleanValue(METRICS_EXPORT_JVM_METRICS_KEY, System::getenv, defaultSettings.metrics()::exportJvmMetrics);
        String path = Settings.value(METRICS_ENDPOINT_KEY, System::getenv, defaultSettings.metrics()::endpoint);
        int port = Settings.intValue(METRICS_PORT_KEY, System::getenv, defaultSettings.metrics()::port);
        return new MetricsSettings(enable, exportJvmMetrics, path, port);
    }
}

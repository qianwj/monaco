package cn.elvis.monaco.settings;

import cn.elvis.monaco.authentication.AuthenticationMode;
import cn.elvis.monaco.transport.TransportType;

import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Application config using properties.
 * @author qianwj
 * @since  0.0.1
 */
//public final class PropertiesSettings implements Settings {
//
//    private static final PropertiesSettings SYSTEM_PROPERTIES_SETTINGS = new PropertiesSettings(
//            EnvironmentSettings.getInstance(),
//            System.getProperties()
//    );
//
//    private final Settings defaultSettings;
//
//    private final Properties properties;
//
//    private final AuthenticationMode authenticationMode;
//
//    private final TransportSettings tcpTransportConfig;
//
//    private final TransportSettings webSocketTransportConfig;
//
//    private final MetricsSettings metricsConfig;
//
//    public PropertiesSettings(Settings defaultSettings, Properties properties) {
//        Objects.requireNonNull(defaultSettings, "default settings must not be null");
//        Objects.requireNonNull(properties, "properties must not be null");
//        this.defaultSettings = defaultSettings;
//        this.properties = properties;
//        this.tcpTransportConfig = getTransportSettings(
//                TransportType.TCP,
//                defaultSettings.tcp(),
//                EnvironmentSettings.TCP_TRANSPORT_KEY_PREFIX
//        );
//        this.webSocketTransportConfig = getTransportSettings(
//                TransportType.WS,
//                defaultSettings.webSocket(),
//                EnvironmentSettings.WS_TRANSPORT_KEY_PREFIX
//        );
//        this.metricsConfig = getMetricsSettings();
//        this.authenticationMode = AuthenticationMode.valueOf(
//                value(EnvironmentSettings.AUTHENTICATION_MODE_KEY, defaultSettings.authenticationMode()::name)
//        );
//    }
//
//    public static PropertiesSettings systemDefault() {
//        return SYSTEM_PROPERTIES_SETTINGS;
//    }
//
//    @Override
//    public int maximumSessionCount() {
//        return intValue(EnvironmentSettings.MAXIMUM_SESSION_COUNT_KEY, defaultSettings::maximumSessionCount);
//    }
//
//    @Override
//    public boolean serverAssignedClientIdentifier() {
//        return booleanValue(EnvironmentSettings.SERVER_ASSIGNED_CLIENT_IDENTIFIER_KEY, defaultSettings::serverAssignedClientIdentifier);
//    }
//
//    @Override
//    public int maximumClientIdentifierLength() {
//        return intValue(EnvironmentSettings.MAXIMUM_CLIENT_IDENTIFIER_LENGTH_KEY, defaultSettings::maximumClientIdentifierLength);
//    }
//
//    @Override
//    public int defaultSessionExpiryInterval() {
//        return intValue(EnvironmentSettings.DEFAULT_SESSION_EXPIRY_INTERVAL_KEY, defaultSettings::defaultSessionExpiryInterval);
//    }
//
//    @Override
//    public int maxSessionExpiryInterval() {
//        return intValue(EnvironmentSettings.MAX_SESSION_EXPIRY_INTERVAL_KEY, defaultSettings::maxSessionExpiryInterval);
//    }
//
//    @Override
//    public int defaultReceiveMaximum() {
//        return intValue(EnvironmentSettings.DEFAULT_RECEIVE_MAXIMUM_KEY, defaultSettings::defaultReceiveMaximum);
//    }
//
//    @Override
//    public int maxReceiveMaximum() {
//        return intValue(EnvironmentSettings.MAX_RECEIVE_MAXIMUM_KEY, defaultSettings::maxReceiveMaximum);
//    }
//
//    @Override
//    public int topicAliasMaximum() {
//        return intValue(EnvironmentSettings.TOPIC_ALIAS_MAXIMUM_KEY, defaultSettings::topicAliasMaximum);
//    }
//
//    @Override
//    public boolean wildcardSubscriptionAvailable() {
//        return booleanValue(EnvironmentSettings.WILDCARD_SUBSCRIPTION_AVAILABLE_KEY, defaultSettings::wildcardSubscriptionAvailable);
//    }
//
//    @Override
//    public int maximumQualityOfService() {
//        return intValue(EnvironmentSettings.MAXIMUM_QOS_KEY, defaultSettings::maximumQualityOfService);
//    }
//
//    @Override
//    public boolean subscriptionIdentifierAvailable() {
//        return booleanValue(EnvironmentSettings.SUBSCRIPTION_IDENTIFIER_AVAILABLE_KEY, defaultSettings::subscriptionIdentifierAvailable);
//    }
//
//    @Override
//    public boolean sharedSubscriptionAvailable() {
//        return booleanValue(EnvironmentSettings.SHARD_SUBSCRIPTION_AVAILABLE_KEY, defaultSettings::sharedSubscriptionAvailable);
//    }
//
//    @Override
//    public boolean retainAvailable() {
//        return booleanValue(EnvironmentSettings.RETAIN_AVAILABLE_KEY, defaultSettings::retainAvailable);
//    }
//
//    @Override
//    public int serverKeepaliveIntervalMaximum() {
//        return intValue(EnvironmentSettings.SERVER_KEEPALIVE_MAXIMUM_KEY, defaultSettings::serverKeepaliveIntervalMaximum);
//    }
//
//    @Override
//    public AuthenticationMode authenticationMode() {
//        return authenticationMode;
//    }
//
//    @Override
//    public String fileAuthenticationPath() {
//        return value(EnvironmentSettings.FILE_AUTHENTICATION_PATH_KEY, defaultSettings::fileAuthenticationPath);
//    }
//
//    @Override
//    public TransportSettings tcp() {
//        return tcpTransportConfig;
//    }
//
//    @Override
//    public TransportSettings webSocket() {
//        return webSocketTransportConfig;
//    }
//
//    @Override
//    public MetricsSettings metrics() {
//        return metricsConfig;
//    }
//
//    private TransportSettings getTransportSettings(TransportType transportType,
//                                                   TransportSettings defaultSettings,
//                                                   String prefix) {
//        boolean enable = booleanValue(prefix + EnvironmentSettings.TRANSPORT_ENABLE_KEY, defaultSettings::enable);
//        int port = intValue(prefix + EnvironmentSettings.TRANSPORT_PORT_KEY, defaultSettings::port);
//        boolean useTLS = booleanValue(prefix + EnvironmentSettings.TRANSPORT_USE_TLS_KEY, defaultSettings::useTLS);
//        int instances = intValue(prefix + EnvironmentSettings.TRANSPORT_INSTANCES_KEY, defaultSettings::instances);
//        return new TransportSettings(transportType, enable, port, useTLS, instances);
//    }
//
//    private MetricsSettings getMetricsSettings() {
//        boolean enable = booleanValue(EnvironmentSettings.METRICS_ENABLED_KEY, defaultSettings.metrics()::enable);
//        boolean exportJvmMetrics = booleanValue(EnvironmentSettings.METRICS_EXPORT_JVM_METRICS_KEY, defaultSettings.metrics()::exportJvmMetrics);
//        String path = value(EnvironmentSettings.METRICS_ENDPOINT_KEY, defaultSettings.metrics()::endpoint);
//        int port = intValue(EnvironmentSettings.METRICS_PORT_KEY, defaultSettings.metrics()::port);
//        return new MetricsSettings(enable, exportJvmMetrics, path, port);
//    }
//
//    private String value(String key, Supplier<String> defaultValueSupplier) {
//        return Settings.value(convertKey(key), properties::getProperty, defaultValueSupplier);
//    }
//
//    private boolean booleanValue(String key, Supplier<? extends Boolean> defaultValueSupplier) {
//        return Settings.booleanValue(
//                convertKey(key),
//                properties::getProperty,
//                defaultValueSupplier
//        );
//    }
//
//    private int intValue(String key, Supplier<? extends Integer> defaultValueSupplier) {
//        return Settings.intValue(
//                convertKey(key),
//                properties::getProperty,
//                defaultValueSupplier
//        );
//    }
//
//    private String convertKey(String key) {
//        return key.replace('_', '.').toLowerCase();
//    }
//}

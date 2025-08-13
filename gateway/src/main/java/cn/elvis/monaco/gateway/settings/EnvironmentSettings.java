package cn.elvis.monaco.gateway.settings;

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

    private static final String MAXIMUM_SESSION_COUNT_KEY = "MAXIMUM_SESSION_COUNT";

    private static final String DEFAULT_SESSION_EXPIRY_INTERVAL_KEY = KEY_PREFIX + "DEFAULT_SESSION_EXPIRY_INTERVAL";

    private static final String MAX_SESSION_EXPIRY_INTERVAL_KEY = KEY_PREFIX + "MAX_SESSION_EXPIRY_INTERVAL";

    private static final String DEFAULT_RECEIVE_MAXIMUM_KEY = KEY_PREFIX + "DEFAULT_RECEIVE_MAXIMUM";

    private static final String TOPIC_ALIAS_MAXIMUM_KEY = KEY_PREFIX + "TOPIC_ALIAS_MAXIMUM";

    private static final String RETAIN_AVAILABLE_KEY = KEY_PREFIX + "RETAIN_AVAILABLE";

    private static final String TCP_TRANSPORT_KEY_PREFIX = KEY_PREFIX + "TCP_TRANSPORT_";

    private static final String WS_TRANSPORT_KEY_PREFIX = KEY_PREFIX + "WS_TRANSPORT_";

    private static final String TRANSPORT_ENABLE_KEY = "ENABLE";

    private static final String TRANSPORT_PORT_KEY = "PORT";

    private static final String TRANSPORT_USE_TLS_KEY = "USE_TLS";

    private static final String TRANSPORT_INSTANCES_KEY = "INSTANCES";

    private final Settings defaultSettings = DefaultSettings.getInstance();

    private final TransportSettings tcpTransportConfig;

    private final TransportSettings webSocketTransportConfig;

    private static final EnvironmentSettings INSTANCE = new EnvironmentSettings();

    private EnvironmentSettings() {
        this.tcpTransportConfig = getTransportSettings(defaultSettings.tcp(), TCP_TRANSPORT_KEY_PREFIX);
        this.webSocketTransportConfig = getTransportSettings(defaultSettings.webSocket(), WS_TRANSPORT_KEY_PREFIX);
    }

    public static Settings getInstance() {
        return INSTANCE;
    }

    @Override
    public int maximumSessionCount() {
        return intValue(MAXIMUM_SESSION_COUNT_KEY, defaultSettings::maximumSessionCount);
    }

    @Override
    public int defaultSessionExpiryInterval() {
        return intValue(DEFAULT_SESSION_EXPIRY_INTERVAL_KEY, defaultSettings::defaultSessionExpiryInterval);
    }

    @Override
    public int maxSessionExpiryInterval() {
        return intValue(MAX_SESSION_EXPIRY_INTERVAL_KEY, defaultSettings::maxSessionExpiryInterval);
    }

    @Override
    public int defaultReceiveMaximum() {
        return intValue(DEFAULT_RECEIVE_MAXIMUM_KEY, defaultSettings::defaultReceiveMaximum);
    }

    @Override
    public boolean retainAvailable() {
        return booleanValue(RETAIN_AVAILABLE_KEY, defaultSettings::retainAvailable);
    }

    @Override
    public int topicAliasMaximum() {
        return intValue(TOPIC_ALIAS_MAXIMUM_KEY, defaultSettings::topicAliasMaximum);
    }

    @Override
    public TransportSettings tcp() {
        return tcpTransportConfig;
    }

    @Override
    public TransportSettings webSocket() {
        return webSocketTransportConfig;
    }

    private TransportSettings getTransportSettings(TransportSettings defaultSettings, String prefix) {
        boolean enable = booleanValue(prefix + TRANSPORT_ENABLE_KEY, defaultSettings::enable);
        int port = intValue(prefix + TRANSPORT_PORT_KEY, defaultSettings::port);
        boolean useTLS = booleanValue(prefix + TRANSPORT_USE_TLS_KEY, defaultSettings::useTLS);
        int instances = intValue(prefix + TRANSPORT_INSTANCES_KEY, defaultSettings::instances);
        return new TransportSettingsImpl(enable, port, useTLS, instances);
    }

    private int intValue(String key, Supplier<? extends Integer> defaultValueSupplier) {
        return value(key).map(Integer::parseInt).orElseGet(defaultValueSupplier);
    }

    private boolean booleanValue(String key, Supplier<? extends Boolean> defaultValueSupplier) {
        return value(key).map(Boolean::parseBoolean).orElseGet(defaultValueSupplier);
    }

    private Optional<String> value(String key) {
        return Optional.ofNullable(System.getenv(key))
                .flatMap(v -> v.isBlank() ? Optional.empty() : Optional.of(v));
    }
}

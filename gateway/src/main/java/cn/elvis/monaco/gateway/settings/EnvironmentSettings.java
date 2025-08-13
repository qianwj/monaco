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

    private static final String TCP_TRANSPORT_ENABLE_KEY = TCP_TRANSPORT_KEY_PREFIX + "ENABLE";

    private static final String TCP_TRANSPORT_PORT_KEY = TCP_TRANSPORT_KEY_PREFIX + "PORT";

    private static final String TCP_TRANSPORT_USE_TLS_KEY = TCP_TRANSPORT_KEY_PREFIX + "USE_TLS";

    private static final String WS_TRANSPORT_KEY_PREFIX = KEY_PREFIX + "WS_TRANSPORT_";

    private static final String WS_TRANSPORT_ENABLE_KEY = WS_TRANSPORT_KEY_PREFIX + "ENABLE";

    private static final String WS_TRANSPORT_PORT_KEY = WS_TRANSPORT_KEY_PREFIX + "PORT";

    private static final String WS_TRANSPORT_USE_TLS_KEY = WS_TRANSPORT_KEY_PREFIX + "USE_TLS";

    private static final String WS_TRANSPORT_PATH_KEY = WS_TRANSPORT_KEY_PREFIX + "PATH";

    private final Settings defaultSettings = DefaultSettings.getInstance();

    private final TCPTransportConfig tcpTransportConfig;

    private final WebSocketTransportConfig webSocketTransportConfig;

    private static final EnvironmentSettings INSTANCE = new EnvironmentSettings();

    private EnvironmentSettings() {
        this.tcpTransportConfig = initTCPTransportConfig();
        this.webSocketTransportConfig = initWebSocketTransportConfig();
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
    public TCPTransportConfig tcpTransportConfig() {
        return tcpTransportConfig;
    }

    @Override
    public WebSocketTransportConfig webSocketTransportConfig() {
        return webSocketTransportConfig;
    }

    private TCPTransportConfig initTCPTransportConfig() {
        boolean enable = booleanValue(TCP_TRANSPORT_ENABLE_KEY, defaultSettings.tcpTransportConfig()::enable);
        int port = intValue(TCP_TRANSPORT_PORT_KEY, defaultSettings.tcpTransportConfig()::port);
        boolean useTLS = booleanValue(TCP_TRANSPORT_USE_TLS_KEY, defaultSettings.tcpTransportConfig()::useTLS);
        return new TCPTransportConfig(enable, port, useTLS);
    }

    private WebSocketTransportConfig initWebSocketTransportConfig() {
        boolean enable = booleanValue(WS_TRANSPORT_ENABLE_KEY, defaultSettings.webSocketTransportConfig()::enable);
        int port = intValue(WS_TRANSPORT_PORT_KEY, defaultSettings.webSocketTransportConfig()::port);
        String path = value(WS_TRANSPORT_PATH_KEY).orElse(defaultSettings.webSocketTransportConfig().path());
        boolean useTLS = booleanValue(WS_TRANSPORT_USE_TLS_KEY, defaultSettings.webSocketTransportConfig()::useTLS);
        return new WebSocketTransportConfig(enable, port, path, useTLS);
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

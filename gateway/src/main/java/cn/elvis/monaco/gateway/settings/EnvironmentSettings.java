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

    private final Settings defaultSettings = DefaultSettings.getInstance();

    private static final EnvironmentSettings INSTANCE = new EnvironmentSettings();

    private EnvironmentSettings() {};

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

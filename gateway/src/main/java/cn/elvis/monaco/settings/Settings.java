package cn.elvis.monaco.settings;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Application settings, management both client settings and server settings
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface Settings {

    int maximumSessionCount();

    boolean serverAssignedClientIdentifier();

    int maximumClientIdentifierLength();

    int defaultSessionExpiryInterval();

    int maxSessionExpiryInterval();

    int defaultReceiveMaximum();

    int maxReceiveMaximum();

    int topicAliasMaximum();

    int publishQueueMaximum();

    boolean retainAvailable();

    TransportSettings tcp();

    TransportSettings webSocket();

    MetricsSettings metrics();

    static int intValue(String key,
                        Function<String, String> valueMapper,
                        Supplier<? extends Integer> defaultValueSupplier) {
        return value(key, valueMapper).map(Integer::parseInt).orElseGet(defaultValueSupplier);
    }

    static boolean booleanValue(String key,
                                Function<String, String> valueMapper,
                                Supplier<? extends Boolean> defaultValueSupplier) {
        return value(key, valueMapper).map(Boolean::parseBoolean).orElseGet(defaultValueSupplier);
    }

    static String value(String key, Function<String, String> valueMapper, Supplier<String> defaultValueSupplier) {
        return value(key, valueMapper).orElseGet(defaultValueSupplier);
    }

    static Optional<String> value(String key, Function<String, String> valueMapper) {
        return Optional.ofNullable(valueMapper.apply(key))
                .flatMap(v -> v.isBlank() ? Optional.empty() : Optional.of(v));
    }
}

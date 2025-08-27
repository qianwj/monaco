package cn.elvis.monaco.settings;

import cn.elvis.monaco.authentication.AuthenticationMode;
import io.netty.util.internal.StringUtil;

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

    boolean wildcardSubscriptionAvailable();

    int maximumQualityOfService();

    boolean subscriptionIdentifierAvailable();

    boolean sharedSubscriptionAvailable();

    boolean retainAvailable();

    /**
     * Maximum server keepalive interval. Time Unit: seconds.
     */
    int serverKeepaliveIntervalMaximum();

    AuthenticationMode authenticationMode();

    String fileAuthenticationPath();

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

    static void validate(Settings settings) {
        if (settings.maximumSessionCount() <= 0) {
            throw new IllegalArgumentException("maximumSessionCount must be greater than 0");
        }
        if (settings.maximumClientIdentifierLength() <= 0) {
            throw new IllegalArgumentException("maximumClientIdentifierLength must be greater than 0");
        }
        if (settings.defaultSessionExpiryInterval() <= 0) {
            throw new IllegalArgumentException("defaultSessionExpiryInterval must be greater than 0");
        }
        if (settings.maxSessionExpiryInterval() <= 0) {
            throw new IllegalArgumentException("maxSessionExpiryInterval must be greater than 0");
        }
        if (settings.defaultReceiveMaximum() <= 0) {
            throw new IllegalArgumentException("defaultReceiveMaximum must be greater than 0");
        }
        if (settings.maxReceiveMaximum() <= 0) {
            throw new IllegalArgumentException("maxReceiveMaximum must be greater than 0");
        }
        if (settings.topicAliasMaximum() <= 0) {
            throw new IllegalArgumentException("topicAliasMaximum must be greater than 0");
        }
        if (settings.serverKeepaliveIntervalMaximum() > 65536 || settings.serverKeepaliveIntervalMaximum() <= 0) {
            throw new IllegalArgumentException("serverKeepaliveIntervalMaximum must be less than 65535 or greater than 0");
        }
        if (settings.authenticationMode() == AuthenticationMode.FILE) {
            if (StringUtil.isNullOrEmpty(settings.fileAuthenticationPath())) {
                throw new IllegalArgumentException("fileAuthenticationPath must not be null or empty");
            }
        }
    }
}

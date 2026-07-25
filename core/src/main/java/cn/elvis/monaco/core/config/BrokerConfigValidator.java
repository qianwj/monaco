package cn.elvis.monaco.core.config;

import java.util.ArrayList;
import java.util.List;

public final class BrokerConfigValidator {

    private BrokerConfigValidator() {}

    public static void validate(BrokerConfig config) {
        List<String> errors = new ArrayList<>();

        if (config.maxPacketSize() < 1 || config.maxPacketSize() > 268_435_456) {
            errors.add("maxPacketSize must be in [1, 268435456]");
        }
        if (config.maximumQoS() == null) {
            errors.add("maximumQoS must not be null");
        }
        if (config.defaultReceiveMaximum() < 1 || config.defaultReceiveMaximum() > 65535) {
            errors.add("defaultReceiveMaximum must be in [1, 65535]");
        }
        if (config.maxReceiveMaximum() < 1 || config.maxReceiveMaximum() > 65535) {
            errors.add("maxReceiveMaximum must be in [1, 65535]");
        }
        if (config.maxReceiveMaximum() < config.defaultReceiveMaximum()) {
            errors.add("maxReceiveMaximum must be >= defaultReceiveMaximum");
        }
        if (config.topicAliasMaximum() < 0 || config.topicAliasMaximum() > 65535) {
            errors.add("topicAliasMaximum must be in [0, 65535]");
        }
        if (config.maxSessionExpiryInterval().compareTo(config.defaultSessionExpiryInterval()) < 0) {
            errors.add("maxSessionExpiryInterval must be >= defaultSessionExpiryInterval");
        }
        if (config.serverKeepAlive().isNegative()) {
            errors.add("serverKeepAlive must not be negative");
        }
        if (!config.serverKeepAlive().isZero()
                && config.serverKeepAlive().toSeconds() > 65535) {
            errors.add("serverKeepAlive must be in [0, 65535] seconds");
        }

        boolean tcpEnabled = config.tcp() != null && config.tcp().enabled();
        boolean wsEnabled = config.webSocket() != null && config.webSocket().enabled();
        if (!tcpEnabled && !wsEnabled) {
            errors.add("at least one transport (TCP or WebSocket) must be enabled");
        }

        validateTransport(config.tcp(), "tcp", errors);
        validateTransport(config.webSocket(), "webSocket", errors);

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "BrokerConfig validation failed:\n- " + String.join("\n- ", errors));
        }
    }

    private static void validateTransport(TransportConfig transport, String name, List<String> errors) {
        if (transport == null) {
            return;
        }
        if (transport.tlsEnabled()) {
            if (transport.tlsCertPath() == null || transport.tlsCertPath().isBlank()) {
                errors.add(name + ".tlsCertPath must not be empty when TLS is enabled");
            }
            if (transport.tlsKeyPath() == null || transport.tlsKeyPath().isBlank()) {
                errors.add(name + ".tlsKeyPath must not be empty when TLS is enabled");
            }
        }
    }
}

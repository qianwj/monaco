package cn.elvis.monaco.plugin.api.model;

import cn.elvis.monaco.protocol.model.ClientId;
import cn.elvis.monaco.protocol.property.UserProperty;

import java.time.Duration;
import java.util.List;

/** Connection policy view after authentication and before session mutation. */
public record ConnectView(
        ClientId clientId,
        boolean cleanStart,
        Duration sessionExpiryInterval,
        int keepAliveSeconds,
        int receiveMaximum,
        long maximumPacketSize,
        int topicAliasMaximum,
        List<UserProperty> userProperties
) {

    public ConnectView {
        if (clientId == null || sessionExpiryInterval == null) {
            throw new IllegalArgumentException("Connect view identity must not be null");
        }
        if (sessionExpiryInterval.isNegative()) {
            throw new IllegalArgumentException("Session expiry interval must not be negative");
        }
        if (keepAliveSeconds < 0 || keepAliveSeconds > 65_535) {
            throw new IllegalArgumentException("Keep alive must be 0-65535 seconds");
        }
        if (receiveMaximum < 1 || receiveMaximum > 65_535) {
            throw new IllegalArgumentException("Receive maximum must be 1-65535");
        }
        if (maximumPacketSize < 1 || maximumPacketSize > 0xffff_ffffL) {
            throw new IllegalArgumentException("Maximum packet size must be an MQTT unsigned 32-bit value");
        }
        if (topicAliasMaximum < 0 || topicAliasMaximum > 65_535) {
            throw new IllegalArgumentException("Topic alias maximum must be 0-65535");
        }
        userProperties = userProperties == null ? List.of() : List.copyOf(userProperties);
    }

    public ConnectView withUserProperties(List<UserProperty> properties) {
        return new ConnectView(clientId, cleanStart, sessionExpiryInterval, keepAliveSeconds,
                receiveMaximum, maximumPacketSize, topicAliasMaximum, properties);
    }
}

package cn.elvis.monaco.core.config;

import cn.elvis.monaco.protocol.model.QoS;

import java.time.Duration;

public record BrokerConfig(
        // 传输
        TransportConfig tcp,
        TransportConfig webSocket,
        // 会话
        int maxConnections,
        boolean serverAssignedClientIdentifier,
        int maxClientIdentifierLength,
        Duration defaultSessionExpiryInterval,
        Duration maxSessionExpiryInterval,
        int defaultReceiveMaximum,
        int maxReceiveMaximum,
        // 协议能力
        int topicAliasMaximum,
        int maxPacketSize,
        int maxQueueSize,
        Duration serverKeepAlive,
        QoS maximumQoS,
        boolean retainAvailable,
        boolean wildcardSubscriptionAvailable,
        boolean subscriptionIdentifierAvailable,
        boolean sharedSubscriptionAvailable,
        // 安全
        String authMode,
        String authFilePath,
        // 存储
        String rocksdbPath,
        // 指标
        MetricsConfig metrics
) {
    /**
     * Keep Alive 超时 = 1.5 倍 serverKeepAlive（MQTT 5.0 §3.1.2.10）。
     * 当 serverKeepAlive 为 ZERO 时返回 ZERO，表示不强制。
     */
    public Duration keepAliveTimeout() {
        if (serverKeepAlive.isZero()) {
            return Duration.ZERO;
        }
        return serverKeepAlive.plus(serverKeepAlive.dividedBy(2));
    }
}

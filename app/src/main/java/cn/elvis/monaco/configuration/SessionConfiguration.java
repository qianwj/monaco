package cn.elvis.monaco.configuration;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;

/**
 * Server side session configuration.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class SessionConfiguration {

    private boolean severAssignedClientId;

    private int maxClientIdentifierLength;

    private long maxSessionExpiryInterval;

    private int receiveMaximumLimit;

    private boolean enableEnhancedAuthenticate;

    private boolean wildcardSubscriptionAvailable;

    private boolean shardSubscriptionAvailable;

    private boolean subscriptionIdentifierAvailable;

    private boolean serverKeepalive;

    public boolean isSeverAssignedClientId() {
        return severAssignedClientId;
    }

    public SessionConfiguration setSeverAssignedClientId(boolean severAssignedClientId) {
        this.severAssignedClientId = severAssignedClientId;
        return this;
    }

    public int getMaxClientIdentifierLength() {
        return maxClientIdentifierLength;
    }

    public SessionConfiguration setMaxClientIdentifierLength(int maxClientIdentifierLength) {
        this.maxClientIdentifierLength = maxClientIdentifierLength;
        return this;
    }

    public long getMaxSessionExpiryInterval() {
        return maxSessionExpiryInterval;
    }

    public SessionConfiguration setMaxSessionExpiryInterval(long maxSessionExpiryInterval) {
        this.maxSessionExpiryInterval = maxSessionExpiryInterval;
        return this;
    }

    public int getReceiveMaximumLimit() {
        return receiveMaximumLimit;
    }

    public SessionConfiguration setReceiveMaximumLimit(int receiveMaximumLimit) {
        this.receiveMaximumLimit = receiveMaximumLimit;
        return this;
    }

    public boolean isEnableEnhancedAuthenticate() {
        return enableEnhancedAuthenticate;
    }

    public SessionConfiguration setEnableEnhancedAuthenticate(boolean enableEnhancedAuthenticate) {
        this.enableEnhancedAuthenticate = enableEnhancedAuthenticate;
        return this;
    }

    public boolean isWildcardSubscriptionAvailable() {
        return wildcardSubscriptionAvailable;
    }

    public SessionConfiguration setWildcardSubscriptionAvailable(boolean wildcardSubscriptionAvailable) {
        this.wildcardSubscriptionAvailable = wildcardSubscriptionAvailable;
        return this;
    }

    public boolean isShardSubscriptionAvailable() {
        return shardSubscriptionAvailable;
    }

    public SessionConfiguration setShardSubscriptionAvailable(boolean shardSubscriptionAvailable) {
        this.shardSubscriptionAvailable = shardSubscriptionAvailable;
        return this;
    }

    public boolean isSubscriptionIdentifierAvailable() {
        return subscriptionIdentifierAvailable;
    }

    public SessionConfiguration setSubscriptionIdentifierAvailable(boolean subscriptionIdentifierAvailable) {
        this.subscriptionIdentifierAvailable = subscriptionIdentifierAvailable;
        return this;
    }

    public boolean isServerKeepalive() {
        return serverKeepalive;
    }

    public SessionConfiguration setServerKeepalive(boolean serverKeepalive) {
        this.serverKeepalive = serverKeepalive;
        return this;
    }

    public MqttPropertiesBuilder toMqttProperties() {
        MqttPropertiesBuilder builder = MqttPropertiesBuilder.create();
        if (wildcardSubscriptionAvailable) {
            builder.withAvailableOption(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE);
        }
        if (shardSubscriptionAvailable) {
            builder.withAvailableOption(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE);
        }
        if (subscriptionIdentifierAvailable) {
            builder.withAvailableOption(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE);
        }
        if (serverKeepalive) {
            builder.withAvailableOption(MqttPropertyType.SERVER_KEEP_ALIVE);
        }
        return builder;
    }
}

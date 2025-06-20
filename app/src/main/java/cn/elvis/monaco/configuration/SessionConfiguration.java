package cn.elvis.monaco.configuration;

public final class SessionConfiguration {

    private boolean severAssignedClientId;

    private int maxClientIdentifierLength;

    private long maxSessionExpiryInterval;

    private int receiveMaximumLimit;

    private boolean enableEnhancedAuthenticate;

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
}

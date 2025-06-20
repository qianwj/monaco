package cn.elvis.monaco.configuration;

public class SessionConfiguration {

    private long maxSessionExpiryInterval;

    private int receiveMaximumLimit;

    private boolean enableEnhancedAuthenticate;

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

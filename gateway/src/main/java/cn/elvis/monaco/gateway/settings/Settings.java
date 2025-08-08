package cn.elvis.monaco.gateway.settings;

/**
 * Application settings, management both client settings and server settings
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface Settings {

    int maximumSessionCount();

    int defaultSessionExpiryInterval();

    int maxSessionExpiryInterval();

    int defaultReceiveMaximum();

    boolean retainAvailable();
}

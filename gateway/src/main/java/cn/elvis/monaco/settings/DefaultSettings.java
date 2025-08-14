package cn.elvis.monaco.settings;

import cn.elvis.monaco.transport.TransportType;

/**
 * Default application settings, use fixed value and unmodifiable.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultSettings implements Settings {

    private static final int MAXIMUM_SESSION_COUNT = 100;

    private static final int DEFAULT_SESSION_EXPIRY_INTERVAL = 30 * 60 * 1000;

    private static final int MAX_SESSION_EXPIRY_INTERVAL = DEFAULT_SESSION_EXPIRY_INTERVAL * 8;

    private static final int DEFAULT_RECEIVE_MAXIMUM = 65535;

    private static final int TOPIC_ALIAS_MAXIMUM = 500;

    private static final int PUBLISH_QUEUE_MAXIMUM = 1000;

    private static final DefaultSettings INSTANCE = new DefaultSettings();

    private DefaultSettings() {}

    public static Settings getInstance() {
        return INSTANCE;
    }

    @Override
    public int maximumSessionCount() {
        return MAXIMUM_SESSION_COUNT;
    }

    @Override
    public int defaultSessionExpiryInterval() {
        return DEFAULT_SESSION_EXPIRY_INTERVAL;
    }

    @Override
    public int maxSessionExpiryInterval() {
        return MAX_SESSION_EXPIRY_INTERVAL;
    }

    @Override
    public int defaultReceiveMaximum() {
        return DEFAULT_RECEIVE_MAXIMUM;
    }

    @Override
    public int topicAliasMaximum() {
        return TOPIC_ALIAS_MAXIMUM;
    }

    @Override
    public int publishQueueMaximum() {
        return PUBLISH_QUEUE_MAXIMUM;
    }

    @Override
    public boolean retainAvailable() {
        return false;
    }

    @Override
    public TransportSettings tcp() {
        return new TransportSettingsImpl(TransportType.TCP, true, 1883, false, 1);
    }

    @Override
    public TransportSettings webSocket() {
        return new TransportSettingsImpl(TransportType.WS, false, 8883, false, 1);
    }
}

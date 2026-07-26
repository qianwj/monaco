package cn.elvis.monaco.plugin.api.decision;

/** Stable rejection categories mapped to legal MQTT reason codes by the Broker. */
public enum PluginRejectReason {
    BAD_CREDENTIALS,
    NOT_AUTHORIZED,
    BANNED,
    BAD_AUTHENTICATION_METHOD,
    QUOTA_EXCEEDED,
    SERVER_BUSY,
    IMPLEMENTATION_ERROR
}

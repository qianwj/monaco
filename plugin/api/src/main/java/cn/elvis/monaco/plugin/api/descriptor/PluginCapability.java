package cn.elvis.monaco.plugin.api.descriptor;

/** Capabilities a plugin may expose through its immutable hook list. */
public enum PluginCapability {
    AUTHENTICATION,
    ENHANCED_AUTHENTICATION,
    AUTHORIZATION,
    CONNECTION_INTERCEPTION,
    PUBLISH_INTERCEPTION,
    SUBSCRIPTION_INTERCEPTION,
    WILL_INTERCEPTION,
    EVENT_LISTENER
}

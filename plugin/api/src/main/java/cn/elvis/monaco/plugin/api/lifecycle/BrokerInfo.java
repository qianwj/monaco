package cn.elvis.monaco.plugin.api.lifecycle;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;

/** Stable Broker identity information visible to a plugin. */
public record BrokerInfo(String brokerVersion, ApiVersion pluginApiVersion, String nodeId) {

    public BrokerInfo {
        if (brokerVersion == null || brokerVersion.isBlank()) {
            throw new IllegalArgumentException("Broker version must not be blank");
        }
        if (pluginApiVersion == null) {
            throw new IllegalArgumentException("Plugin API version must not be null");
        }
        nodeId = nodeId == null ? "" : nodeId;
    }

    public boolean clustered() {
        return !nodeId.isEmpty();
    }
}

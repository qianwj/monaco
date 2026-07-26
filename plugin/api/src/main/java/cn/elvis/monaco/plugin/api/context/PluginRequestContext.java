package cn.elvis.monaco.plugin.api.context;

import cn.elvis.monaco.protocol.model.ClientId;
import cn.elvis.monaco.protocol.model.ConnectionId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Immutable metadata shared by all decision hooks in one invocation. */
public record PluginRequestContext(
        String invocationId,
        ConnectionId connectionId,
        ClientId clientId,
        Optional<PluginPrincipal> principal,
        String listenerName,
        String remoteAddress,
        Optional<TlsInfo> tls,
        MessageOrigin origin,
        String traceId,
        Map<String, String> attributes,
        Instant receivedAt
) {

    public PluginRequestContext {
        if (invocationId == null || invocationId.isBlank()) {
            throw new IllegalArgumentException("Plugin invocation id must not be blank");
        }
        if (connectionId == null || clientId == null || origin == null || receivedAt == null) {
            throw new IllegalArgumentException("Plugin request context identity must not be null");
        }
        principal = principal == null ? Optional.empty() : principal;
        listenerName = listenerName == null ? "" : listenerName;
        remoteAddress = remoteAddress == null ? "" : remoteAddress;
        tls = tls == null ? Optional.empty() : tls;
        traceId = traceId == null ? "" : traceId;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String toString() {
        return "PluginRequestContext[invocationId=" + invocationId
                + ", connectionId=" + connectionId
                + ", clientId=" + clientId
                + ", principal=" + principal
                + ", listenerName=" + listenerName
                + ", remoteAddress=" + remoteAddress
                + ", tls=" + tls
                + ", origin=" + origin
                + ", traceId=" + traceId
                + ", attributeKeys=" + attributes.keySet()
                + ", receivedAt=" + receivedAt + ']';
    }
}

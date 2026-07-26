package cn.elvis.monaco.plugin.api.event;

import cn.elvis.monaco.plugin.api.context.MessageOrigin;
import cn.elvis.monaco.protocol.model.ClientId;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.TopicFilter;
import cn.elvis.monaco.protocol.model.TopicName;

import java.time.Instant;
import java.util.List;

/**
 * Immutable, best-effort notification emitted after Broker state commit.
 * Listeners must be idempotent because delivery is not exactly once.
 */
public sealed interface PluginEvent {

    String eventId();

    Instant occurredAt();

    record BrokerStarted(
            String eventId,
            Instant occurredAt,
            String brokerVersion,
            String nodeId
    ) implements PluginEvent {
        public BrokerStarted {
            requireCommon(eventId, occurredAt);
            if (brokerVersion == null || brokerVersion.isBlank()) {
                throw new IllegalArgumentException("Broker version must not be blank");
            }
            nodeId = nodeId == null ? "" : nodeId;
        }
    }

    record BrokerStopping(
            String eventId,
            Instant occurredAt,
            String nodeId
    ) implements PluginEvent {
        public BrokerStopping {
            requireCommon(eventId, occurredAt);
            nodeId = nodeId == null ? "" : nodeId;
        }
    }

    record SessionConnected(
            String eventId,
            Instant occurredAt,
            ClientId clientId,
            ConnectionId connectionId,
            boolean cleanStart
    ) implements PluginEvent {
        public SessionConnected {
            requireCommon(eventId, occurredAt);
            require(clientId, "Client id");
            require(connectionId, "Connection id");
        }
    }

    record SessionDisconnected(
            String eventId,
            Instant occurredAt,
            ClientId clientId,
            ConnectionId connectionId,
            DisconnectKind kind
    ) implements PluginEvent {
        public SessionDisconnected {
            requireCommon(eventId, occurredAt);
            require(clientId, "Client id");
            require(connectionId, "Connection id");
            require(kind, "Disconnect kind");
        }
    }

    record SessionExpired(
            String eventId,
            Instant occurredAt,
            ClientId clientId
    ) implements PluginEvent {
        public SessionExpired {
            requireCommon(eventId, occurredAt);
            require(clientId, "Client id");
        }
    }

    record MessageAccepted(
            String eventId,
            Instant occurredAt,
            String messageId,
            ClientId publisherId,
            TopicName topic,
            QoS qos,
            MessageOrigin origin
    ) implements PluginEvent {
        public MessageAccepted {
            requireCommon(eventId, occurredAt);
            requireText(messageId, "Message id");
            require(publisherId, "Publisher id");
            require(topic, "Topic");
            require(qos, "QoS");
            require(origin, "Message origin");
        }
    }

    record MessageDelivered(
            String eventId,
            Instant occurredAt,
            String messageId,
            ClientId subscriberId,
            QoS qos
    ) implements PluginEvent {
        public MessageDelivered {
            requireCommon(eventId, occurredAt);
            requireText(messageId, "Message id");
            require(subscriberId, "Subscriber id");
            require(qos, "QoS");
        }
    }

    record MessageDropped(
            String eventId,
            Instant occurredAt,
            String messageId,
            ClientId clientId,
            MessageDropKind kind
    ) implements PluginEvent {
        public MessageDropped {
            requireCommon(eventId, occurredAt);
            requireText(messageId, "Message id");
            require(clientId, "Client id");
            require(kind, "Message drop kind");
        }
    }

    record SubscriptionChanged(
            String eventId,
            Instant occurredAt,
            ClientId clientId,
            List<TopicFilter> added,
            List<TopicFilter> removed
    ) implements PluginEvent {
        public SubscriptionChanged {
            requireCommon(eventId, occurredAt);
            require(clientId, "Client id");
            added = added == null ? List.of() : List.copyOf(added);
            removed = removed == null ? List.of() : List.copyOf(removed);
        }
    }

    record RetainedChanged(
            String eventId,
            Instant occurredAt,
            TopicName topic,
            boolean present
    ) implements PluginEvent {
        public RetainedChanged {
            requireCommon(eventId, occurredAt);
            require(topic, "Topic");
        }
    }

    record WillScheduled(
            String eventId,
            Instant occurredAt,
            ClientId clientId,
            TopicName topic,
            Instant dueAt
    ) implements PluginEvent {
        public WillScheduled {
            requireCommon(eventId, occurredAt);
            require(clientId, "Client id");
            require(topic, "Topic");
            require(dueAt, "Will due time");
        }
    }

    record WillCancelled(
            String eventId,
            Instant occurredAt,
            ClientId clientId,
            TopicName topic,
            WillCancelKind kind
    ) implements PluginEvent {
        public WillCancelled {
            requireCommon(eventId, occurredAt);
            require(clientId, "Client id");
            require(topic, "Topic");
            require(kind, "Will cancel kind");
        }
    }

    record WillPublished(
            String eventId,
            Instant occurredAt,
            ClientId clientId,
            TopicName topic,
            String messageId
    ) implements PluginEvent {
        public WillPublished {
            requireCommon(eventId, occurredAt);
            require(clientId, "Client id");
            require(topic, "Topic");
            requireText(messageId, "Message id");
        }
    }

    record PluginStateChanged(
            String eventId,
            Instant occurredAt,
            String pluginId,
            PluginState previous,
            PluginState current,
            String detail
    ) implements PluginEvent {
        public PluginStateChanged {
            requireCommon(eventId, occurredAt);
            requireText(pluginId, "Plugin id");
            require(previous, "Previous plugin state");
            require(current, "Current plugin state");
            detail = detail == null ? "" : detail;
        }
    }

    enum DisconnectKind {
        NORMAL,
        NETWORK_ERROR,
        KEEP_ALIVE_TIMEOUT,
        PROTOCOL_ERROR,
        SESSION_TAKEN_OVER,
        SERVER_SHUTDOWN
    }

    enum MessageDropKind {
        EXPIRED,
        QUEUE_FULL,
        NOT_AUTHORIZED,
        QUOTA_EXCEEDED,
        ADMINISTRATIVE_ACTION
    }

    enum WillCancelKind {
        NORMAL_DISCONNECT,
        SESSION_RECONNECTED,
        SESSION_REMOVED,
        ADMINISTRATIVE_ACTION
    }

    enum PluginState {
        DISCOVERED,
        VALIDATED,
        LOADED,
        STARTING,
        ACTIVE,
        DEGRADED,
        STOPPING,
        STOPPED,
        FAILED
    }

    private static void requireCommon(String eventId, Instant occurredAt) {
        requireText(eventId, "Event id");
        require(occurredAt, "Event time");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
    }
}

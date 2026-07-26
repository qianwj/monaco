package cn.elvis.monaco.core.command;

import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.core.state.InflightRecord;
import cn.elvis.monaco.core.state.SubscriptionRecord;
import cn.elvis.monaco.core.state.WillRecord;
import cn.elvis.monaco.protocol.packet.ServerPacket;

import java.util.List;

/**
 * Side-effect descriptions produced by transitions.
 * Executed by the runtime layer, not the domain.
 */
public sealed interface Action {

    // --- Network actions (targeted by ConnectionRef to prevent stale delivery) ---

    record SendPacket(ConnectionRef target, ServerPacket packet) implements Action {
    }

    record CloseConnection(ConnectionRef target) implements Action {
    }

    // --- Keep alive ---

    record StartKeepAliveTimer(String clientId, int keepAliveSeconds) implements Action {
    }

    record ResetKeepAliveTimer(String clientId) implements Action {
    }

    // --- Session persistence ---

    record PersistSession(String clientId) implements Action {
    }

    record RemoveSession(String clientId) implements Action {
    }

    // --- Session expiry ---

    record ScheduleSessionExpiry(String clientId, long expirySeconds) implements Action {
    }

    record CancelSessionExpiry(String clientId) implements Action {
    }

    // --- Will ---

    record PersistWill(WillRecord willRecord) implements Action {
    }

    record RemoveWill(String clientId) implements Action {
    }

    record ScheduleWillPublish(String clientId, long delaySeconds) implements Action {
    }

    record PublishWill(String clientId) implements Action {
    }

    // --- Subscriptions ---

    record PersistSubscriptions(String clientId, List<SubscriptionRecord> records) implements Action {
    }

    record RemoveSubscriptions(String clientId, List<String> topicFilters) implements Action {
    }

    record ClearAllSubscriptions(String clientId) implements Action {
    }

    // --- Inflight / delivery ---

    record PersistInflight(InflightRecord record) implements Action {
    }

    record RemoveInflight(String clientId, InflightRecord.Direction direction, int packetId) implements Action {
    }

    record ClearAllInflight(String clientId) implements Action {
    }

    // --- Message delivery ---

    record DeliverMessage(ConnectionRef target, ServerPacket.Publish publish) implements Action {
    }
}

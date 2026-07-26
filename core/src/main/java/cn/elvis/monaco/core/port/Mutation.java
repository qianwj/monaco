package cn.elvis.monaco.core.port;

import cn.elvis.monaco.core.state.InflightRecord;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.state.SubscriptionRecord;
import cn.elvis.monaco.core.state.WillRecord;
import cn.elvis.monaco.protocol.packet.ServerPacket;

import java.util.List;

/**
 * Individual state mutations produced by transitions.
 * Grouped into a {@link MutationBatch} for atomic commit.
 */
public sealed interface Mutation {

    // --- Session ---
    record SaveSession(SessionRecord session) implements Mutation {}
    record RemoveSession(String clientId) implements Mutation {}

    // --- Subscriptions ---
    record SaveSubscriptions(String clientId, List<SubscriptionRecord> records) implements Mutation {}
    record RemoveSubscriptions(String clientId, List<String> topicFilters) implements Mutation {}
    record ClearSubscriptions(String clientId) implements Mutation {}

    // --- Will ---
    record SaveWill(WillRecord will) implements Mutation {}
    record RemoveWill(String clientId) implements Mutation {}

    // --- Inflight ---
    record AddInflight(String clientId, InflightRecord record) implements Mutation {}
    record RemoveInflight(String clientId, InflightRecord.Direction direction, int packetId) implements Mutation {}
    record ClearInflight(String clientId) implements Mutation {}

    // --- Retain ---
    record SaveRetain(String topicName, ServerPacket.Publish message) implements Mutation {}
    record RemoveRetain(String topicName) implements Mutation {}

    // --- Pending messages ---
    record EnqueueMessage(String clientId, String messageId, ServerPacket.Publish message) implements Mutation {}
    record DequeueMessage(String clientId, String messageId) implements Mutation {}
}

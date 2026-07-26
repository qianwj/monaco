package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.core.port.*;
import cn.elvis.monaco.core.state.*;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of {@link BrokerStore}.
 * <p>
 * Atomicity is provided by synchronizing on the per-client shard lock.
 * Suitable for standalone/testing use.
 */
public class InMemoryBrokerStore implements BrokerStore {

    private final ConcurrentHashMap<String, Shard> shards = new ConcurrentHashMap<>();

    @Override
    public Mono<ShardSnapshot> load(String clientId) {
        return Mono.fromSupplier(() -> {
            var shard = shards.get(clientId);
            if (shard == null) {
                return ShardSnapshot.empty(clientId);
            }
            synchronized (shard) {
                return new ShardSnapshot(
                        shard.session,
                        List.copyOf(shard.subscriptions),
                        List.copyOf(shard.inflightRecords),
                        shard.will
                );
            }
        });
    }

    @Override
    public Mono<CommitResult> commit(StoreCommit commit) {
        return Mono.fromSupplier(() -> {
            var shard = shards.computeIfAbsent(commit.clientId(), k -> new Shard());
            synchronized (shard) {
                if (shard.revision != commit.expectedRevision()) {
                    return new CommitResult.ConflictRevision(shard.revision);
                }

                for (var mutation : commit.mutations().mutations()) {
                    applyMutation(shard, mutation);
                }

                shard.revision++;
                return (CommitResult) new CommitResult.Success(shard.revision);
            }
        });
    }

    private void applyMutation(Shard shard, Mutation mutation) {
        switch (mutation) {
            case Mutation.SaveSession m -> shard.session = m.session();
            case Mutation.RemoveSession m -> shard.session = null;

            case Mutation.SaveSubscriptions m -> {
                for (var record : m.records()) {
                    shard.subscriptions.removeIf(r -> r.topicFilter().equals(record.topicFilter()));
                    shard.subscriptions.add(record);
                }
            }
            case Mutation.RemoveSubscriptions m ->
                    shard.subscriptions.removeIf(r -> m.topicFilters().contains(r.topicFilter()));
            case Mutation.ClearSubscriptions m -> shard.subscriptions.clear();

            case Mutation.SaveWill m -> shard.will = m.will();
            case Mutation.RemoveWill m -> shard.will = null;

            case Mutation.AddInflight m -> shard.inflightRecords.add(m.record());
            case Mutation.RemoveInflight m ->
                    shard.inflightRecords.removeIf(r ->
                            r.direction() == m.direction() && r.packetId() == m.packetId());
            case Mutation.ClearInflight m -> shard.inflightRecords.clear();

            case Mutation.SaveRetain m ->
                    retainMessages.put(m.topicName(), m.message());
            case Mutation.RemoveRetain m -> retainMessages.remove(m.topicName());

            case Mutation.EnqueueMessage m ->
                    pendingMessages.computeIfAbsent(m.clientId(), k -> new CopyOnWriteArrayList<>())
                            .add(new PendingEntry(m.messageId(), m.message()));
            case Mutation.DequeueMessage m -> {
                var list = pendingMessages.get(m.clientId());
                if (list != null) {
                    list.removeIf(e -> e.messageId.equals(m.messageId()));
                }
            }
        }
    }

    // --- Global state (not per-shard) ---
    private final ConcurrentHashMap<String, ServerPacket.Publish> retainMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<PendingEntry>> pendingMessages = new ConcurrentHashMap<>();

    private record PendingEntry(String messageId, ServerPacket.Publish message) {}

    private static class Shard {
        long revision = 0;
        SessionRecord session;
        final CopyOnWriteArrayList<SubscriptionRecord> subscriptions = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<InflightRecord> inflightRecords = new CopyOnWriteArrayList<>();
        WillRecord will;
    }
}

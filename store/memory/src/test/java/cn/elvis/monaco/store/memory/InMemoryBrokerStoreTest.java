package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.core.port.*;
import cn.elvis.monaco.core.state.SubscriptionRecord;
import cn.elvis.monaco.protocol.model.QoS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryBrokerStoreTest {

    private InMemoryBrokerStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryBrokerStore();
    }

    @Test
    void loadEmptyReturnsEmptySnapshot() {
        var snapshot = store.load("unknown").block();
        assertNotNull(snapshot);
        assertNull(snapshot.session());
        assertTrue(snapshot.subscriptions().isEmpty());
    }

    @Test
    void commitSavesSubscriptions() {
        var sub = subscription("client1", "sensor/+/temp");
        var commit = StoreCommit.of("client1", 0,
                MutationBatch.of(List.of(
                        new Mutation.SaveSubscriptions("client1", List.of(sub))
                )));

        var result = store.commit(commit).block();
        assertInstanceOf(CommitResult.Success.class, result);

        var snapshot = store.load("client1").block();
        assertEquals(1, snapshot.subscriptions().size());
        assertEquals("sensor/+/temp", snapshot.subscriptions().getFirst().topicFilter());
    }

    @Test
    void commitReplacesExistingSubscription() {
        var sub1 = subscription("client1", "a/b");
        var sub2 = new SubscriptionRecord("client1", "a/b", QoS.EXACTLY_ONCE,
                false, false, 0, 0, 2, Instant.now());

        store.commit(StoreCommit.of("client1", 0,
                MutationBatch.of(List.of(new Mutation.SaveSubscriptions("client1", List.of(sub1)))))).block();
        store.commit(StoreCommit.of("client1", 1,
                MutationBatch.of(List.of(new Mutation.SaveSubscriptions("client1", List.of(sub2)))))).block();

        var snapshot = store.load("client1").block();
        assertEquals(1, snapshot.subscriptions().size());
        assertEquals(QoS.EXACTLY_ONCE, snapshot.subscriptions().getFirst().maxQoS());
    }

    @Test
    void commitRemovesSubscriptions() {
        var sub1 = subscription("client1", "a/b");
        var sub2 = subscription("client1", "c/d");
        store.commit(StoreCommit.of("client1", 0,
                MutationBatch.of(List.of(new Mutation.SaveSubscriptions("client1", List.of(sub1, sub2)))))).block();

        store.commit(StoreCommit.of("client1", 1,
                MutationBatch.of(List.of(new Mutation.RemoveSubscriptions("client1", List.of("a/b")))))).block();

        var snapshot = store.load("client1").block();
        assertEquals(1, snapshot.subscriptions().size());
        assertEquals("c/d", snapshot.subscriptions().getFirst().topicFilter());
    }

    @Test
    void commitConflictsOnWrongRevision() {
        store.commit(StoreCommit.of("client1", 0,
                MutationBatch.of(List.of(new Mutation.SaveSubscriptions("client1", List.of(subscription("client1", "a/b"))))))).block();

        var result = store.commit(StoreCommit.of("client1", 0,
                MutationBatch.of(List.of(new Mutation.RemoveSubscriptions("client1", List.of("a/b")))))).block();

        assertInstanceOf(CommitResult.ConflictRevision.class, result);
    }

    @Test
    void commitClearsSubscriptions() {
        store.commit(StoreCommit.of("client1", 0,
                MutationBatch.of(List.of(new Mutation.SaveSubscriptions("client1",
                        List.of(subscription("client1", "a/b"), subscription("client1", "c/d"))))))).block();

        store.commit(StoreCommit.of("client1", 1,
                MutationBatch.of(List.of(new Mutation.ClearSubscriptions("client1"))))).block();

        var snapshot = store.load("client1").block();
        assertTrue(snapshot.subscriptions().isEmpty());
    }

    private SubscriptionRecord subscription(String clientId, String topicFilter) {
        return new SubscriptionRecord(clientId, topicFilter, QoS.AT_LEAST_ONCE,
                false, false, 0, 0, 1, Instant.now());
    }
}

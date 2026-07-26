package cn.elvis.monaco.testkit.port;

import cn.elvis.monaco.core.port.BrokerStore;
import cn.elvis.monaco.core.port.CommitResult;
import cn.elvis.monaco.core.port.MutationBatch;
import cn.elvis.monaco.core.port.ShardSnapshot;
import cn.elvis.monaco.core.port.StoreCommit;
import cn.elvis.monaco.testkit.probe.StoreTraceEvent;
import cn.elvis.monaco.testkit.probe.TraceProbe;
import cn.elvis.monaco.testkit.time.MutableBrokerClock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RecordingBrokerStoreTest {

    @Test
    void recordsLoadAndCommitLifecycleInOneTrace() {
        BrokerStore delegate = new BrokerStore() {
            @Override
            public Mono<ShardSnapshot> load(String clientId) {
                return Mono.just(ShardSnapshot.empty(clientId));
            }

            @Override
            public Mono<CommitResult> commit(StoreCommit commit) {
                return Mono.just(new CommitResult.Success(1));
            }
        };
        TraceProbe trace = new TraceProbe(
                MutableBrokerClock.startingAt(Instant.parse("2026-07-26T00:00:00Z")));
        RecordingBrokerStore store = new RecordingBrokerStore(delegate, trace);
        StoreCommit commit = StoreCommit.of("client-1", 0, MutationBatch.empty());

        StepVerifier.create(store.load("client-1")).expectNextCount(1).verifyComplete();
        StepVerifier.create(store.commit(commit))
                .expectNext(new CommitResult.Success(1))
                .verifyComplete();

        assertEquals(4, trace.snapshot().size());
        assertInstanceOf(StoreTraceEvent.Load.class, trace.snapshot().get(0).event());
        assertInstanceOf(StoreTraceEvent.Loaded.class, trace.snapshot().get(1).event());
        assertInstanceOf(StoreTraceEvent.Commit.class, trace.snapshot().get(2).event());
        StoreTraceEvent.Committed committed = assertInstanceOf(
                StoreTraceEvent.Committed.class, trace.snapshot().get(3).event());
        assertEquals(commit, committed.commit());
        assertEquals(new CommitResult.Success(1), committed.result());
    }
}

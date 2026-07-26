package cn.elvis.monaco.testkit.fault;

import cn.elvis.monaco.core.port.BrokerStore;
import cn.elvis.monaco.core.port.CommitResult;
import cn.elvis.monaco.core.port.MutationBatch;
import cn.elvis.monaco.core.port.ShardSnapshot;
import cn.elvis.monaco.core.port.StoreCommit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaultInjectingBrokerStoreTest {

    @Test
    void failureBeforeCommitDoesNotCallDelegate() {
        AtomicInteger commits = new AtomicInteger();
        BrokerStore delegate = storeReturning(commits, new CommitResult.Success(1));
        FaultPlan faults = FaultPlan.builder()
                .failOnce(FaultPoint.STORE_BEFORE_COMMIT, new TestException("before commit"))
                .build();
        FaultInjectingBrokerStore store = new FaultInjectingBrokerStore(delegate, faults);

        StepVerifier.create(store.commit(commit()))
                .expectErrorMessage("before commit")
                .verify();

        assertEquals(0, commits.get());
        assertEquals(0, faults.invocationCount(FaultPoint.STORE_AFTER_COMMIT));
    }

    @Test
    void failureAfterCommitKeepsSuccessfulDelegateCommit() {
        AtomicInteger commits = new AtomicInteger();
        BrokerStore delegate = storeReturning(commits, new CommitResult.Success(1));
        FaultPlan faults = FaultPlan.builder()
                .failOnce(FaultPoint.STORE_AFTER_COMMIT, new TestException("result unknown"))
                .build();
        FaultInjectingBrokerStore store = new FaultInjectingBrokerStore(delegate, faults);

        StepVerifier.create(store.commit(commit()))
                .expectErrorMessage("result unknown")
                .verify();

        assertEquals(1, commits.get());
        assertEquals(1, faults.invocationCount(FaultPoint.STORE_AFTER_COMMIT));
    }

    @Test
    void conflictDoesNotTriggerAfterCommitFault() {
        AtomicInteger commits = new AtomicInteger();
        CommitResult conflict = new CommitResult.ConflictRevision(4);
        FaultPlan faults = FaultPlan.builder()
                .failAlways(FaultPoint.STORE_AFTER_COMMIT, () -> new TestException("must not run"))
                .build();
        FaultInjectingBrokerStore store = new FaultInjectingBrokerStore(
                storeReturning(commits, conflict), faults);

        StepVerifier.create(store.commit(commit())).expectNext(conflict).verifyComplete();

        assertEquals(1, commits.get());
        assertEquals(0, faults.invocationCount(FaultPoint.STORE_AFTER_COMMIT));
    }

    @Test
    void loadUsesBeforeOperationFaultPoint() {
        AtomicInteger loads = new AtomicInteger();
        BrokerStore delegate = new BrokerStore() {
            @Override
            public Mono<ShardSnapshot> load(String clientId) {
                loads.incrementAndGet();
                return Mono.just(ShardSnapshot.empty(clientId));
            }

            @Override
            public Mono<CommitResult> commit(StoreCommit commit) {
                return Mono.just(new CommitResult.Success(1));
            }
        };
        FaultPlan faults = FaultPlan.builder()
                .failOnce(FaultPoint.STORE_BEFORE_OPERATION, new TestException("before load"))
                .build();
        FaultInjectingBrokerStore store = new FaultInjectingBrokerStore(delegate, faults);

        StepVerifier.create(store.load("client-1")).expectErrorMessage("before load").verify();

        assertEquals(0, loads.get());
    }

    private static BrokerStore storeReturning(AtomicInteger commits, CommitResult result) {
        return new BrokerStore() {
            @Override
            public Mono<ShardSnapshot> load(String clientId) {
                return Mono.just(ShardSnapshot.empty(clientId));
            }

            @Override
            public Mono<CommitResult> commit(StoreCommit commit) {
                commits.incrementAndGet();
                return Mono.just(result);
            }
        };
    }

    private static StoreCommit commit() {
        return StoreCommit.of("client-1", 0, MutationBatch.empty());
    }

    private static final class TestException extends RuntimeException {

        private TestException(String message) {
            super(message);
        }
    }
}

package cn.elvis.monaco.testkit.fault;

import cn.elvis.monaco.core.port.BrokerStore;
import cn.elvis.monaco.core.port.CommitResult;
import cn.elvis.monaco.core.port.ShardSnapshot;
import cn.elvis.monaco.core.port.StoreCommit;
import reactor.core.publisher.Mono;

import java.util.Objects;

public final class FaultInjectingBrokerStore implements BrokerStore {

    private final BrokerStore delegate;
    private final FaultPlan faults;

    public FaultInjectingBrokerStore(BrokerStore delegate, FaultPlan faults) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.faults = Objects.requireNonNull(faults, "faults");
    }

    @Override
    public Mono<ShardSnapshot> load(String clientId) {
        Objects.requireNonNull(clientId, "clientId");
        return faults.trigger(FaultPoint.STORE_BEFORE_OPERATION)
                .then(Mono.defer(() -> Objects.requireNonNull(
                        delegate.load(clientId), "delegate load returned null")));
    }

    @Override
    public Mono<CommitResult> commit(StoreCommit commit) {
        Objects.requireNonNull(commit, "commit");
        return faults.trigger(FaultPoint.STORE_BEFORE_OPERATION)
                .then(faults.trigger(FaultPoint.STORE_BEFORE_COMMIT))
                .then(Mono.defer(() -> Objects.requireNonNull(
                        delegate.commit(commit), "delegate commit returned null")))
                .flatMap(result -> afterSuccessfulCommit(result));
    }

    private Mono<CommitResult> afterSuccessfulCommit(CommitResult result) {
        if (!(result instanceof CommitResult.Success)) {
            return Mono.just(result);
        }
        return faults.trigger(FaultPoint.STORE_AFTER_COMMIT).thenReturn(result);
    }
}

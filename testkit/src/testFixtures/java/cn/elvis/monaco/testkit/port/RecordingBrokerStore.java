package cn.elvis.monaco.testkit.port;

import cn.elvis.monaco.core.port.BrokerStore;
import cn.elvis.monaco.core.port.CommitResult;
import cn.elvis.monaco.core.port.ShardSnapshot;
import cn.elvis.monaco.core.port.StoreCommit;
import cn.elvis.monaco.testkit.probe.StoreTraceEvent;
import cn.elvis.monaco.testkit.probe.TraceProbe;
import reactor.core.publisher.Mono;

import java.util.Objects;

public final class RecordingBrokerStore implements BrokerStore {

    private final BrokerStore delegate;
    private final TraceProbe trace;

    public RecordingBrokerStore(BrokerStore delegate, TraceProbe trace) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.trace = Objects.requireNonNull(trace, "trace");
    }

    @Override
    public Mono<ShardSnapshot> load(String clientId) {
        Objects.requireNonNull(clientId, "clientId");
        return Mono.defer(() -> {
            trace.record(new StoreTraceEvent.Load(clientId));
            return Objects.requireNonNull(delegate.load(clientId), "delegate load returned null");
        }).doOnNext(snapshot -> trace.record(new StoreTraceEvent.Loaded(clientId, snapshot)))
                .doOnError(trace::recordError);
    }

    @Override
    public Mono<CommitResult> commit(StoreCommit commit) {
        Objects.requireNonNull(commit, "commit");
        return Mono.defer(() -> {
            trace.record(new StoreTraceEvent.Commit(commit));
            return Objects.requireNonNull(delegate.commit(commit), "delegate commit returned null");
        }).doOnNext(result -> trace.record(new StoreTraceEvent.Committed(commit, result)))
                .doOnError(trace::recordError);
    }
}

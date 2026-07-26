package cn.elvis.monaco.testkit.fault;

import cn.elvis.monaco.core.port.ConnectionSink;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import reactor.core.publisher.Mono;

import java.util.Objects;

public final class FaultInjectingConnectionSink implements ConnectionSink {

    private final ConnectionSink delegate;
    private final FaultPlan faults;

    public FaultInjectingConnectionSink(ConnectionSink delegate, FaultPlan faults) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.faults = Objects.requireNonNull(faults, "faults");
    }

    @Override
    public Mono<Void> send(ConnectionRef target, ServerPacket packet) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(packet, "packet");
        return faults.trigger(FaultPoint.SINK_BEFORE_SEND)
                .then(Mono.defer(() -> Objects.requireNonNull(
                        delegate.send(target, packet), "delegate send returned null")))
                .then(faults.trigger(FaultPoint.SINK_AFTER_SEND));
    }

    @Override
    public Mono<Void> close(ConnectionRef target) {
        Objects.requireNonNull(target, "target");
        return faults.trigger(FaultPoint.SINK_CLOSE)
                .then(Mono.defer(() -> Objects.requireNonNull(
                        delegate.close(target), "delegate close returned null")));
    }
}

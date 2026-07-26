package cn.elvis.monaco.testkit.port;

import cn.elvis.monaco.core.port.ConnectionSink;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.testkit.probe.ConnectionTraceEvent;
import cn.elvis.monaco.testkit.probe.EventProbe;
import cn.elvis.monaco.testkit.probe.PacketProbe;
import cn.elvis.monaco.testkit.probe.TraceProbe;
import reactor.core.publisher.Mono;

import java.util.Objects;

public final class RecordingConnectionSink implements ConnectionSink {

    private final TraceProbe trace;
    private final SendBehavior sendBehavior;
    private final CloseBehavior closeBehavior;
    private final PacketProbe<ServerPacket> packets = new PacketProbe<>("connection packets");
    private final EventProbe<ConnectionRef> closes = new EventProbe<>("connection closes");

    public RecordingConnectionSink(TraceProbe trace) {
        this(trace, (target, packet) -> Mono.empty(), target -> Mono.empty());
    }

    public RecordingConnectionSink(
            TraceProbe trace,
            SendBehavior sendBehavior,
            CloseBehavior closeBehavior) {
        this.trace = Objects.requireNonNull(trace, "trace");
        this.sendBehavior = Objects.requireNonNull(sendBehavior, "sendBehavior");
        this.closeBehavior = Objects.requireNonNull(closeBehavior, "closeBehavior");
    }

    @Override
    public Mono<Void> send(ConnectionRef target, ServerPacket packet) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(packet, "packet");
        return Mono.defer(() -> {
            packets.record(packet);
            trace.record(new ConnectionTraceEvent.Send(target, packet));
            return Objects.requireNonNull(
                    sendBehavior.send(target, packet), "sendBehavior returned null");
        }).doOnError(trace::recordError);
    }

    @Override
    public Mono<Void> close(ConnectionRef target) {
        Objects.requireNonNull(target, "target");
        return Mono.defer(() -> {
            closes.record(target);
            trace.record(new ConnectionTraceEvent.Close(target));
            return Objects.requireNonNull(closeBehavior.close(target), "closeBehavior returned null");
        }).doOnError(trace::recordError);
    }

    public PacketProbe<ServerPacket> packets() {
        return packets;
    }

    public EventProbe<ConnectionRef> closes() {
        return closes;
    }

    @FunctionalInterface
    public interface SendBehavior {
        Mono<Void> send(ConnectionRef target, ServerPacket packet);
    }

    @FunctionalInterface
    public interface CloseBehavior {
        Mono<Void> close(ConnectionRef target);
    }
}

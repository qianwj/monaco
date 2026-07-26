package cn.elvis.monaco.testkit.probe;

import cn.elvis.monaco.protocol.packet.ServerPacket;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

public final class PacketProbe {

    private final ProbeBuffer<ServerPacket> buffer;

    public PacketProbe(String name) {
        this.buffer = new ProbeBuffer<>(name);
    }

    public void record(ServerPacket packet) {
        buffer.record(packet);
    }

    public void recordError(Throwable error) {
        buffer.recordError(error);
    }

    public List<ServerPacket> snapshot() {
        return buffer.snapshot();
    }

    public List<ServerPacket> awaitCount(int expectedCount, Duration timeout) {
        return buffer.awaitCount(expectedCount, timeout);
    }

    public ServerPacket awaitMatching(Predicate<? super ServerPacket> predicate, Duration timeout) {
        return buffer.awaitMatching(predicate, timeout);
    }
}

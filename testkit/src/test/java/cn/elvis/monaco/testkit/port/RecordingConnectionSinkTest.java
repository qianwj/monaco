package cn.elvis.monaco.testkit.port;

import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.testkit.fault.Gate;
import cn.elvis.monaco.testkit.probe.ConnectionTraceEvent;
import cn.elvis.monaco.testkit.probe.TraceProbe;
import cn.elvis.monaco.testkit.time.MutableBrokerClock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RecordingConnectionSinkTest {

    @Test
    void recordsPacketsClosesAndUnifiedTrace() {
        TraceProbe trace = trace();
        RecordingConnectionSink sink = new RecordingConnectionSink(trace);
        ConnectionRef connection = ConnectionRef.local("connection-1", 2);
        ServerPacket packet = new ServerPacket.PingResp();

        StepVerifier.create(sink.send(connection, packet)).verifyComplete();
        StepVerifier.create(sink.close(connection)).verifyComplete();

        assertEquals(List.of(packet), sink.packets().snapshot());
        assertEquals(List.of(connection), sink.closes().snapshot());
        assertEquals(2, trace.snapshot().size());
        ConnectionTraceEvent.Send send = assertInstanceOf(
                ConnectionTraceEvent.Send.class, trace.snapshot().get(0).event());
        ConnectionTraceEvent.Close close = assertInstanceOf(
                ConnectionTraceEvent.Close.class, trace.snapshot().get(1).event());
        assertEquals(connection, send.target());
        assertEquals(packet, send.packet());
        assertEquals(connection, close.target());
    }

    @Test
    void delegatesDelayAndFailureBehaviorWithoutBlocking() {
        Gate sendGate = new Gate("connection send");
        TestException closeFailure = new TestException("close failed");
        RecordingConnectionSink sink = new RecordingConnectionSink(
                trace(),
                (target, packet) -> sendGate.await(),
                target -> Mono.error(closeFailure));

        StepVerifier.create(sink.send(
                        ConnectionRef.local("connection-1", 1), new ServerPacket.PingResp()))
                .then(() -> {
                    sendGate.awaitArrivals(1, Duration.ofSeconds(1));
                    sendGate.release();
                })
                .verifyComplete();
        StepVerifier.create(sink.close(ConnectionRef.local("connection-1", 1)))
                .expectErrorSatisfies(error -> assertEquals(closeFailure, error))
                .verify();
    }

    private static TraceProbe trace() {
        return new TraceProbe(MutableBrokerClock.startingAt(Instant.parse("2026-07-26T00:00:00Z")));
    }

    private static final class TestException extends RuntimeException {

        private TestException(String message) {
            super(message);
        }
    }
}

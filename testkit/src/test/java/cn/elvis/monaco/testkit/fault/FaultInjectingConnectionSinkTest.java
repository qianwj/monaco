package cn.elvis.monaco.testkit.fault;

import cn.elvis.monaco.core.port.ConnectionSink;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaultInjectingConnectionSinkTest {

    @Test
    void beforeSendFailureSkipsDelegateAndAfterSendFailureKeepsSend() {
        AtomicInteger sends = new AtomicInteger();
        ConnectionSink delegate = sink(sends, new AtomicInteger());
        ConnectionRef connection = ConnectionRef.local("connection-1", 1);
        ServerPacket packet = new ServerPacket.PingResp();
        FaultPlan faults = FaultPlan.builder()
                .failOnce(FaultPoint.SINK_BEFORE_SEND, new TestException("before send"))
                .failOnce(FaultPoint.SINK_AFTER_SEND, new TestException("after send"))
                .build();
        FaultInjectingConnectionSink sink = new FaultInjectingConnectionSink(delegate, faults);

        StepVerifier.create(sink.send(connection, packet)).expectErrorMessage("before send").verify();
        assertEquals(0, sends.get());

        StepVerifier.create(sink.send(connection, packet)).expectErrorMessage("after send").verify();
        assertEquals(1, sends.get());
    }

    @Test
    void closeFaultSkipsDelegate() {
        AtomicInteger closes = new AtomicInteger();
        FaultPlan faults = FaultPlan.builder()
                .failOnce(FaultPoint.SINK_CLOSE, new TestException("close"))
                .build();
        FaultInjectingConnectionSink sink = new FaultInjectingConnectionSink(
                sink(new AtomicInteger(), closes), faults);

        StepVerifier.create(sink.close(ConnectionRef.local("connection-1", 1)))
                .expectErrorMessage("close")
                .verify();

        assertEquals(0, closes.get());
    }

    private static ConnectionSink sink(AtomicInteger sends, AtomicInteger closes) {
        return new ConnectionSink() {
            @Override
            public Mono<Void> send(ConnectionRef target, ServerPacket packet) {
                sends.incrementAndGet();
                return Mono.empty();
            }

            @Override
            public Mono<Void> close(ConnectionRef target) {
                closes.incrementAndGet();
                return Mono.empty();
            }
        };
    }

    private static final class TestException extends RuntimeException {

        private TestException(String message) {
            super(message);
        }
    }
}

package cn.elvis.monaco.testkit.probe;

import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.packet.ServerPacket;

import java.util.Objects;

public sealed interface ConnectionTraceEvent extends TraceEvent {

    record Send(ConnectionRef target, ServerPacket packet) implements ConnectionTraceEvent {

        public Send {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(packet, "packet");
        }
    }

    record Close(ConnectionRef target) implements ConnectionTraceEvent {

        public Close {
            Objects.requireNonNull(target, "target");
        }
    }
}

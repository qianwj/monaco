package cn.elvis.monaco.core.command;

import cn.elvis.monaco.protocol.packet.ClientPacket;

import java.time.Instant;

/**
 * Domain commands wrapping client packets with runtime context.
 */
public sealed interface Command {

    Instant timestamp();

    record Connect(
            ClientPacket.Connect packet,
            String assignedClientId,
            Instant timestamp
    ) implements Command {
    }

    record Publish(
            ClientPacket.Publish packet,
            String clientId,
            Instant timestamp
    ) implements Command {
    }

    record Subscribe(
            ClientPacket.Subscribe packet,
            String clientId,
            Instant timestamp
    ) implements Command {
    }

    record Unsubscribe(
            ClientPacket.Unsubscribe packet,
            String clientId,
            Instant timestamp
    ) implements Command {
    }

    record Disconnect(
            ClientPacket.Disconnect packet,
            String clientId,
            Instant timestamp
    ) implements Command {
    }

    record PubAck(
            ClientPacket.PubAck packet,
            String clientId,
            Instant timestamp
    ) implements Command {
    }

    record PubRec(
            ClientPacket.PubRec packet,
            String clientId,
            Instant timestamp
    ) implements Command {
    }

    record PubRel(
            ClientPacket.PubRel packet,
            String clientId,
            Instant timestamp
    ) implements Command {
    }

    record PubComp(
            ClientPacket.PubComp packet,
            String clientId,
            Instant timestamp
    ) implements Command {
    }

    record PingReq(
            String clientId,
            Instant timestamp
    ) implements Command {
    }
}

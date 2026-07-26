package cn.elvis.monaco.testkit.assertion;

import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.protocol.packet.ServerPacket;

public final class PacketAssertions {

    private PacketAssertions() {
    }

    public static void assertPacketEquals(ServerPacket expected, ServerPacket actual) {
        DeepAssertions.assertDeepEquals(expected, actual, "packet");
    }

    public static void assertPacketEquals(ClientPacket expected, ClientPacket actual) {
        DeepAssertions.assertDeepEquals(expected, actual, "packet");
    }
}

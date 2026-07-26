package cn.elvis.monaco.testkit.assertion;

import cn.elvis.monaco.protocol.model.Payload;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.protocol.property.ConnectProperties;
import cn.elvis.monaco.protocol.property.PublishProperties;
import cn.elvis.monaco.protocol.property.UserProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketAssertionsTest {

    @Test
    void comparesPayloadOptionalArraysAndOrderedPropertiesByContent() {
        ServerPacket.Publish expected = publish(new byte[]{1, 2}, new byte[]{3, 4});
        ServerPacket.Publish actual = publish(new byte[]{1, 2}, new byte[]{3, 4});

        assertDoesNotThrow(() -> PacketAssertions.assertPacketEquals(expected, actual));
    }

    @Test
    void reportsExactNestedDifference() {
        ServerPacket.Publish expected = publish(new byte[]{1, 2}, new byte[]{3, 4});
        ServerPacket.Publish actual = publish(new byte[]{1, 9}, new byte[]{3, 4});

        AssertionError error = assertThrows(AssertionError.class,
                () -> PacketAssertions.assertPacketEquals(expected, actual));

        assertTrue(error.getMessage().contains("packet.payload.data[1]"));
    }

    @Test
    void comparesClientPasswordByContent() {
        ClientPacket.Connect expected = new ClientPacket.Connect(
                "client", true, 30, null, "user", new byte[]{5, 6}, ConnectProperties.empty());
        ClientPacket.Connect actual = new ClientPacket.Connect(
                "client", true, 30, null, "user", new byte[]{5, 6}, ConnectProperties.empty());

        assertDoesNotThrow(() -> PacketAssertions.assertPacketEquals(expected, actual));
    }

    private static ServerPacket.Publish publish(byte[] payload, byte[] correlationData) {
        PublishProperties properties = new PublishProperties(
                Optional.of(1),
                Optional.of(30),
                Optional.empty(),
                Optional.empty(),
                Optional.of(correlationData),
                List.of(7, 8),
                Optional.of("application/octet-stream"),
                List.of(new UserProperty("key", "first"), new UserProperty("key", "second")));
        return new ServerPacket.Publish(
                "sensors/temp", QoS.AT_LEAST_ONCE, false, false, 9,
                new Payload(payload), properties);
    }
}

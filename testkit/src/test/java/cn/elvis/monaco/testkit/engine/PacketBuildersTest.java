package cn.elvis.monaco.testkit.engine;

import cn.elvis.monaco.protocol.model.Payload;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.protocol.packet.Subscription;
import cn.elvis.monaco.protocol.property.AckProperties;
import cn.elvis.monaco.protocol.property.ConnectProperties;
import cn.elvis.monaco.protocol.property.PublishProperties;
import cn.elvis.monaco.protocol.property.SubscribeProperties;
import cn.elvis.monaco.protocol.reason.ReasonCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketBuildersTest {

    @Test
    void createsLegalDefaultsWithProtocolFieldsVisible() {
        ClientPacket.Connect connect = PacketBuilders.connect().build();
        ClientPacket.Publish publish = PacketBuilders.publish().build();
        ClientPacket.Subscribe subscribe = PacketBuilders.subscribe().build();
        ClientPacket.Unsubscribe unsubscribe = PacketBuilders.unsubscribe().build();

        assertAll(
                () -> assertEquals("test-client", connect.clientId()),
                () -> assertTrue(connect.cleanStart()),
                () -> assertEquals(60, connect.keepAlive()),
                () -> assertNull(connect.will()),
                () -> assertNull(connect.username()),
                () -> assertNull(connect.password()),
                () -> assertEquals(ConnectProperties.empty(), connect.properties()),
                () -> assertEquals("test/topic", publish.topicName()),
                () -> assertEquals(QoS.AT_MOST_ONCE, publish.qos()),
                () -> assertEquals(0, publish.packetId()),
                () -> assertFalse(publish.retain()),
                () -> assertFalse(publish.dup()),
                () -> assertTrue(publish.payload().isEmpty()),
                () -> assertEquals(List.of(PacketBuilders.subscription("test/#")), subscribe.subscriptions()),
                () -> assertEquals(1, subscribe.packetId()),
                () -> assertEquals(List.of("test/#"), unsubscribe.topicFilters()),
                () -> assertEquals(1, unsubscribe.packetId()),
                () -> assertInstanceOf(ClientPacket.PingReq.class, PacketBuilders.pingReq()),
                () -> assertEquals(ReasonCode.NORMAL_DISCONNECTION,
                        PacketBuilders.disconnect().build().reasonCode()));
    }

    @Test
    void preservesExplicitConnectPublishAndSubscriptionValues() {
        byte[] password = {1, 2};
        ConnectProperties connectProperties = ConnectProperties.empty();
        ClientPacket.Connect connect = PacketBuilders.connect()
                .clientId("custom-client")
                .cleanStart(false)
                .keepAlive(17)
                .credentials("user", password)
                .properties(connectProperties)
                .build();
        password[0] = 9;

        PublishProperties publishProperties = new PublishProperties(
                Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(4, 4), Optional.of("text/plain"), List.of());
        ClientPacket.Publish publish = PacketBuilders.publish()
                .topicName("custom/topic")
                .qos(QoS.EXACTLY_ONCE)
                .retain(true)
                .dup(true)
                .packetId(42)
                .payload(new Payload(new byte[]{3, 4}, Payload.FormatIndicator.UTF8))
                .properties(publishProperties)
                .build();

        Subscription first = PacketBuilders.subscription("a/+");
        Subscription second = new Subscription(
                "b/#", 2, true, true, Subscription.RetainHandling.DO_NOT_SEND);
        SubscribeProperties subscribeProperties = SubscribeProperties.empty();
        ClientPacket.Subscribe subscribe = PacketBuilders.subscribe()
                .packetId(8)
                .subscriptions(first, second)
                .properties(subscribeProperties)
                .build();

        assertAll(
                () -> assertEquals("custom-client", connect.clientId()),
                () -> assertFalse(connect.cleanStart()),
                () -> assertEquals(17, connect.keepAlive()),
                () -> assertEquals("user", connect.username()),
                () -> assertArrayEquals(new byte[]{1, 2}, connect.password()),
                () -> assertEquals(connectProperties, connect.properties()),
                () -> assertEquals("custom/topic", publish.topicName()),
                () -> assertEquals(QoS.EXACTLY_ONCE, publish.qos()),
                () -> assertTrue(publish.retain()),
                () -> assertTrue(publish.dup()),
                () -> assertEquals(42, publish.packetId()),
                () -> assertArrayEquals(new byte[]{3, 4}, publish.payload().data()),
                () -> assertEquals(Payload.FormatIndicator.UTF8, publish.payload().formatIndicator()),
                () -> assertEquals(publishProperties, publish.properties()),
                () -> assertEquals(8, subscribe.packetId()),
                () -> assertEquals(List.of(first, second), subscribe.subscriptions()),
                () -> assertEquals(subscribeProperties, subscribe.properties()));
    }

    @Test
    void buildsEveryAcknowledgementTypeWithExplicitFields() {
        AckProperties properties = new AckProperties(Optional.of("expected"), List.of());

        ClientPacket.PubAck pubAck = PacketBuilders.pubAck()
                .packetId(10)
                .reasonCode(ReasonCode.NO_MATCHING_SUBSCRIBERS)
                .properties(properties)
                .build();
        ClientPacket.PubRec pubRec = PacketBuilders.pubRec()
                .packetId(11)
                .reasonCode(ReasonCode.SUCCESS)
                .properties(properties)
                .build();
        ClientPacket.PubRel pubRel = PacketBuilders.pubRel()
                .packetId(12)
                .reasonCode(ReasonCode.PACKET_IDENTIFIER_NOT_FOUND)
                .properties(properties)
                .build();
        ClientPacket.PubComp pubComp = PacketBuilders.pubComp()
                .packetId(13)
                .reasonCode(ReasonCode.SUCCESS)
                .properties(properties)
                .build();

        assertAll(
                () -> assertEquals(10, pubAck.packetId()),
                () -> assertEquals(ReasonCode.NO_MATCHING_SUBSCRIBERS, pubAck.reasonCode()),
                () -> assertEquals(properties, pubAck.properties()),
                () -> assertEquals(11, pubRec.packetId()),
                () -> assertEquals(12, pubRel.packetId()),
                () -> assertEquals(ReasonCode.PACKET_IDENTIFIER_NOT_FOUND, pubRel.reasonCode()),
                () -> assertEquals(13, pubComp.packetId()));
    }

    @Test
    void preservesExplicitUnsubscribeAndDisconnectValues() {
        AckProperties unsubscribeProperties = new AckProperties(Optional.of("remove"), List.of());
        ClientPacket.Unsubscribe unsubscribe = PacketBuilders.unsubscribe()
                .packetId(9)
                .topicFilters("a/+", "b/#")
                .properties(unsubscribeProperties)
                .build();
        ClientPacket.Disconnect disconnect = PacketBuilders.disconnect()
                .reasonCode(ReasonCode.DISCONNECT_WITH_WILL)
                .build();

        assertAll(
                () -> assertEquals(9, unsubscribe.packetId()),
                () -> assertEquals(List.of("a/+", "b/#"), unsubscribe.topicFilters()),
                () -> assertEquals(unsubscribeProperties, unsubscribe.properties()),
                () -> assertEquals(ReasonCode.DISCONNECT_WITH_WILL, disconnect.reasonCode()));
    }

    @Test
    void doesNotApplyProtocolValidationOrHiddenCorrections() {
        ClientPacket.Connect connect = PacketBuilders.connect()
                .clientId("")
                .keepAlive(-1)
                .build();
        ClientPacket.Publish publish = PacketBuilders.publish()
                .qos(QoS.AT_LEAST_ONCE)
                .packetId(0)
                .build();
        ClientPacket.Subscribe subscribe = PacketBuilders.subscribe()
                .subscriptions(List.of())
                .build();

        assertAll(
                () -> assertEquals("", connect.clientId()),
                () -> assertEquals(-1, connect.keepAlive()),
                () -> assertEquals(QoS.AT_LEAST_ONCE, publish.qos()),
                () -> assertEquals(0, publish.packetId()),
                () -> assertTrue(subscribe.subscriptions().isEmpty()));
    }
}

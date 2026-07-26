package cn.elvis.monaco.plugin.contract;

import cn.elvis.monaco.plugin.api.context.MessageOrigin;
import cn.elvis.monaco.plugin.api.model.PluginPayload;
import cn.elvis.monaco.plugin.api.model.PublishView;
import cn.elvis.monaco.plugin.api.model.SubscriptionView;
import cn.elvis.monaco.protocol.model.PacketId;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.TopicFilter;
import cn.elvis.monaco.protocol.model.TopicName;
import cn.elvis.monaco.protocol.packet.Subscription;
import cn.elvis.monaco.protocol.property.UserProperty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViewContractTest {

    @Test
    void publishModificationCannotIncreaseQosAndPreservesPacketIdentity() {
        PublishView view = publication(QoS.AT_LEAST_ONCE);

        PublishView modified = view.withMessage(
                new TopicName("modified/topic"),
                PluginPayload.of(new byte[]{3}),
                QoS.AT_MOST_ONCE,
                List.of(new UserProperty("source", "plugin")));

        assertEquals(new TopicName("modified/topic"), modified.topic());
        assertEquals(Optional.of(new PacketId(7)), modified.packetId());
        assertThrows(IllegalArgumentException.class,
                () -> view.withMessage(view.topic(), view.payload(), QoS.EXACTLY_ONCE, List.of()));
    }

    @Test
    void publishCopiesUserProperties() {
        List<UserProperty> properties = new ArrayList<>();
        properties.add(new UserProperty("key", "value"));
        PublishView view = new PublishView(
                new TopicName("a/b"), PluginPayload.empty(), QoS.AT_MOST_ONCE,
                false, false, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), properties, MessageOrigin.CLIENT);
        properties.clear();

        assertEquals(List.of(new UserProperty("key", "value")), view.userProperties());
    }

    @Test
    void subscriptionCanOnlyLowerQos() {
        SubscriptionView view = new SubscriptionView(
                new TopicFilter("a/+"), QoS.AT_LEAST_ONCE, false, false,
                Subscription.RetainHandling.SEND_AT_SUBSCRIBE, List.of(1));

        assertEquals(QoS.AT_MOST_ONCE, view.withMaximumQos(QoS.AT_MOST_ONCE).maximumQos());
        assertThrows(IllegalArgumentException.class, () -> view.withMaximumQos(QoS.EXACTLY_ONCE));
    }

    private static PublishView publication(QoS qos) {
        return new PublishView(
                new TopicName("original/topic"),
                PluginPayload.of(new byte[]{1, 2}),
                qos,
                false,
                false,
                Optional.of(new PacketId(7)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                MessageOrigin.CLIENT);
    }
}

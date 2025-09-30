package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.impl.MqttTopicSubscriptionImpl;
import org.junit.jupiter.api.Test;

public class TopicTreeTest {

    @Test
    public void testAddSubscription() {
        TopicTree tree = Topics.createNormalTree();
        Subscription subscription = Subscription.of(
                "test",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/b/c", MqttQoS.AT_LEAST_ONCE)
        );
        tree.addSubscription(subscription);
        var subscriptions = tree.subscriptions(Topics.createTopic("a/b/c"));
        assert subscriptions.size() == 1;
    }
}

package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.impl.MqttTopicSubscriptionImpl;
import org.junit.jupiter.api.Test;

public class TopicTreeTest {

    @Test
    public void testAddSubscription() {
        TopicForest tree = TopicForest.create();
        Subscription subscription1 = Subscription.of(
                "test",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/b/c", MqttQoS.AT_LEAST_ONCE)
        );
        tree.addSubscription(subscription1);
        tree.addSubscription(Subscription.of(
                "test",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/b/d", MqttQoS.AT_LEAST_ONCE)
        ));
        tree.addSubscription(Subscription.of(
                "test",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/d/b", MqttQoS.AT_LEAST_ONCE)
        ));
        var subscriptions = tree.subscriptions(Topics.createTopic("a/b/c"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.size() == 1;
        assert subscriptions.getFirst().equals(subscription1);
        var subscription2 = Subscription.of(
                "test",
                MqttQoS.AT_MOST_ONCE,
                new MqttTopicSubscriptionImpl("a/+/c", MqttQoS.AT_MOST_ONCE)
        );
        tree.addSubscription(subscription2);
        subscriptions = tree.subscriptions(Topics.createTopic("a/+/c"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.size() == 2;
        assert subscriptions.get(1).topicFilter().equals("a/+/c");
        subscriptions = tree.subscriptions(Topics.createTopic("a/d/c"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.size() == 1;
        assert subscriptions.getFirst().equals(subscription2);
        subscriptions = tree.subscriptions(Topics.createTopic("a/+/c"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.size() == 2;
        assert subscriptions.getFirst().equals(subscription1);
        assert subscriptions.get(1).equals(subscription2);
        var subscription3 = Subscription.of(
                "test",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/b/b/c", MqttQoS.AT_LEAST_ONCE)
        );
        tree.addSubscription(subscription3);
        subscriptions = tree.subscriptions(Topics.createTopic("a/+/c"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.size() == 2;
        assert subscriptions.getFirst().equals(subscription1);
        assert subscriptions.get(1).equals(subscription2);
        subscriptions = tree.subscriptions(Topics.createTopic("a/#"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.size() == 5;
    }


    @Test
    public void testRemoveSubscription() {
        TopicForest tree = TopicForest.create();
        Subscription subscription1 = Subscription.of(
                "test",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/b/c", MqttQoS.AT_LEAST_ONCE)
        );
        var subscription2 = Subscription.of(
                "test",
                MqttQoS.AT_MOST_ONCE,
                new MqttTopicSubscriptionImpl("a/+/c", MqttQoS.AT_MOST_ONCE)
        );
        tree.addSubscription(subscription1);
        tree.addSubscription(subscription2);
        tree.print();
        tree.removeSubscription(Topics.createTopic("a/b/c"), "test");
        var subscriptions = tree.subscriptions(Topics.createTopic("a/b/c"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.size() == 1;
        tree.removeSubscription(Topics.createTopic("a/+/c"), "test");
        tree.print();
        subscriptions = tree.subscriptions(Topics.createTopic("a/+/c"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.isEmpty();
        tree.addSubscription(Subscription.of(
                "test",
                MqttQoS.AT_MOST_ONCE,
                new MqttTopicSubscriptionImpl("a/#", MqttQoS.AT_MOST_ONCE)
        ));
        tree.print();
        tree.removeSubscription(Topics.createTopic("a/+/c"), "test");
        tree.print();
        assert tree.subscriptions(Topics.createTopic("a/+/c")).size() == 1;
        tree.removeSubscription(Topics.createTopic("a/#"), "test");
        subscriptions = tree.subscriptions(Topics.createTopic("a/#"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.isEmpty();
    }

    @Test
    public void testRemoveSingleWildcardSubscription() {
        TopicForest tree = TopicForest.create();
        tree.addSubscription(Subscription.of(
                "rrr",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/+/c", MqttQoS.AT_LEAST_ONCE)
        ));
        tree.print();
        tree.removeSubscription(Topics.createTopic("a/+/c"), "rrr");
        tree.print();
        var subscriptions = tree.subscriptions(Topics.createTopic("a/+/c"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.isEmpty();
    }

    @Test
    public void testRemoveMultiWildcardSubscription() {
        TopicForest tree = TopicForest.create();
        tree.addSubscription(Subscription.of(
                "rrr",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/#", MqttQoS.AT_LEAST_ONCE)
        ));
        tree.removeSubscription(Topics.createTopic("a/#"), "rrr");
        tree.print();
        var subscriptions = tree.subscriptions(Topics.createTopic("a/#"));
        System.out.println("subscriptions: " + subscriptions);
        assert subscriptions.isEmpty();
    }

    @Test
    public void testSingleWildcardMatchMultiWildcardSubscription() {
        TopicForest tree = TopicForest.create();
        tree.addSubscription(Subscription.of(
                "rrr",
                MqttQoS.AT_LEAST_ONCE,
                new MqttTopicSubscriptionImpl("a/#", MqttQoS.AT_LEAST_ONCE)
        ));
        var subscriptions = tree.subscriptions(Topics.createTopic("a/+/c"));
        assert subscriptions.size() == 1;
    }

}

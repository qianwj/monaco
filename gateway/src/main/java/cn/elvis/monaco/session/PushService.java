package cn.elvis.monaco.session;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.store.MessageStore;
import cn.elvis.monaco.store.SubscriptionStore;
import io.vertx.core.AbstractVerticle;

public class PushService extends AbstractVerticle {

    private final MessageStore messageStore;

    private final SubscriptionStore subscriptionStore;

    public PushService(MessageStore messageStore, SubscriptionStore subscriptionStore) {
        this.messageStore = messageStore;
        this.subscriptionStore = subscriptionStore;
    }

    @Override
    public void start() throws Exception {
        vertx.eventBus().consumer(ChannelKeys.MESSAGE_PUBLISH_CHANNEL, event -> {
           var msg = event.body();

        });
    }
}

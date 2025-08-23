package cn.elvis.monaco.session;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.exception.ProtocolException;
import cn.elvis.monaco.store.ClientSessionStore;
import cn.elvis.monaco.store.SubscriptionStore;
import cn.elvis.monaco.topics.Topics;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

/**
 * push message to client
 *
 * @author qianwj
 * @since  0.0.1
 */
public class PushService extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final SubscriptionStore subscriptionStore;

    private final ClientSessionStore clientSessionStore;

    private MessageConsumer<PublishMessage> consumer;

    public PushService(SubscriptionStore subscriptionStore,
                       ClientSessionStore clientSessionStore) {
        this.subscriptionStore = subscriptionStore;
        this.clientSessionStore = clientSessionStore;
    }

    @Override
    public void start() throws Exception {
        this.consumer = vertx.eventBus().consumer(ChannelKeys.MESSAGE_PUBLISH_CHANNEL, event -> {
           var msg = event.body();
           subscriptionStore.search(Topics.createTopic(msg.topic()), subscription -> {
               clientSessionStore.get(subscription.clientId()).ifPresent(session -> {
                   try {
                       session.push(msg);
                   } catch (ProtocolException e) {
                       log.warn("push message error: ", e);
                   }
               });
           });
        });
    }

    @Override
    public void stop() throws Exception {
        if (this.consumer != null) {
            consumer.unregister();
        }
    }
}

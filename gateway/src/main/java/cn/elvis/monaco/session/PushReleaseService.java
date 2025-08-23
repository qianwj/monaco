package cn.elvis.monaco.session;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.store.ClientSessionStore;
import cn.elvis.monaco.store.MessageStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;

public class PushReleaseService extends AbstractVerticle {

    private final MessageStore messageStore;

    private final ClientSessionStore clientSessionStore;

    private MessageConsumer<Integer> consumer;

    public PushReleaseService(MessageStore messageStore, ClientSessionStore clientSessionStore) {
        this.messageStore = messageStore;
        this.clientSessionStore = clientSessionStore;
    }

    @Override
    public void start() throws Exception {
        this.consumer = vertx.eventBus().consumer(ChannelKeys.PUBLISH_RELEASE_CHANNEL, event -> {
           messageStore.getReceivers(event.body()).forEach(clientId -> {
               clientSessionStore.get(clientId).ifPresent(session -> {
                  session.releasePush(event.body());
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

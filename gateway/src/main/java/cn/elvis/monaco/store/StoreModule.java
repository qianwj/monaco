package cn.elvis.monaco.store;

import cn.elvis.monaco.Module;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.memory.*;
import io.vertx.core.Vertx;

public final class StoreModule extends Module {

    public StoreModule(Vertx vertx, Settings settings) {
        super(vertx, settings);
    }

    public void init(Module... dependency) {
        instances.put(ClientSessionStore.class, new MemoryClientSessionStore());
        instances.put(TopicAliasStore.class, new MemoryTopicAliasStore());
        instances.put(SubscriptionStore.class, new MemorySubscriptionStore());
        instances.put(RetainMessageStore.class, new MemoryRetainMessageStore());
        instances.put(MessageStore.class, new MemoryMessageStore());
    }
}

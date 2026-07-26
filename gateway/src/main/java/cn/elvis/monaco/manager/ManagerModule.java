package cn.elvis.monaco.manager;

import cn.elvis.monaco.Module;
import cn.elvis.monaco.manager.standalone.*;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.*;
import io.vertx.core.Vertx;

public final class ManagerModule extends Module {


    public ManagerModule(Vertx vertx, Settings settings) {
        super(vertx, settings);
    }

    /**
     * @param dependency: first dependency is StoreModule.
     */
    @Override
    public void init(Module... dependency) {
        var storeModule = dependency[0];
        var topicAliasStore = storeModule.instance(TopicAliasStore.class);
        var clientSessionStore = storeModule.instance(ClientSessionStore.class);
        var messageStore = storeModule.instance(MessageStore.class);
        var retainMessageStore = storeModule.instance(RetainMessageStore.class);
        var subscriptionStore = storeModule.instance(SubscriptionStore.class);

        final ClientSessionManager clientSessionManager = new DefaultClientSessionManager(settings, vertx, topicAliasStore, clientSessionStore, messageStore);
        final PacketIdentifierManager packetIdentifierManager = new DefaultPacketIdentifierManager();
        final PublisherManager publisherManager = new DefaultPublisherManager(settings, vertx, clientSessionStore, topicAliasStore, retainMessageStore);
        final SubscriberManager subscriberManager = new DefaultSubscriberManager(settings, vertx.eventBus(), subscriptionStore);
        final WillManager willManager = new DefaultWillManager(vertx.eventBus());

        instances.put(ClientSessionManager.class, clientSessionManager);
        instances.put(PacketIdentifierManager.class, packetIdentifierManager);
        instances.put(PublisherManager.class, publisherManager);
        instances.put(SubscriberManager.class, subscriberManager);
        instances.put(WillManager.class, willManager);
    }
}

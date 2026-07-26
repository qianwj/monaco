package cn.elvis.monaco.session;

import cn.elvis.monaco.Module;
import cn.elvis.monaco.manager.*;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.ClientSessionStore;
import cn.elvis.monaco.store.SubscriptionStore;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;

public final class SessionModule extends Module {

    public SessionModule(Vertx vertx, Settings settings) {
        super(vertx, settings);
    }

    /**
     *
     * @param dependency: first dependency is StoreModule, second is ManagerModule
     */
    @Override
    public void init(Module... dependency) {
        final Module store = dependency[0];
        final Module manager = dependency[1];

        final EndpointHandler handler = new EndpointHandler(
                manager.instance(ClientSessionManager.class),
                manager.instance(PacketIdentifierManager.class),
                manager.instance(PublisherManager.class),
                manager.instance(SubscriberManager.class),
                manager.instance(WillManager.class)
        );
        vertx.deployVerticle(
                () -> new PushService(store.instance(SubscriptionStore.class), store.instance(ClientSessionStore.class)),
                new DeploymentOptions()
                        .setInstances(1)
                        .setThreadingModel(ThreadingModel.WORKER)
        );
    }
}

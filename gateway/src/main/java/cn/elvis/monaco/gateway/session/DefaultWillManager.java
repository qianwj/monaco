package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.ChannelKeys;
import cn.elvis.monaco.gateway.entity.WillMessage;
import cn.elvis.monaco.gateway.listener.ClientSessionCloseListener;
import io.netty.util.HashedWheelTimer;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class DefaultWillManager implements WillManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultWillManager.class);

    private final Map<String, WillMessage> store = new ConcurrentHashMap<>();

    private final HashedWheelTimer timer = new HashedWheelTimer();

    private final ClientSessionCloseListener clientSessionCloseListener;

    public DefaultWillManager(EventBus eventBus) {
        timer.start();
        this.clientSessionCloseListener = new ClientSessionCloseListener(eventBus, clientId ->
                Optional.ofNullable(store.get(clientId)).ifPresent(willMessage ->
                    timer.newTimeout(timeout -> {
                        if (timeout.isCancelled()) {
                            log.info("Timer cancelled");
                            store.remove(clientId);
                            return;
                        }
                        if (!willMessage.expired()) {
                            try {
                                eventBus.publish(ChannelKeys.WILL_MESSAGE_PUBLISH_CHANNEL, willMessage);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            store.remove(clientId);
                        }
                    }, willMessage.delayInterval().getSeconds(), TimeUnit.SECONDS)));
    }

    @Override
    public void addWill(String clientId, WillMessage willMessage) {
        log.info("add will from client[" + clientId + "]");
        store.put(clientId, willMessage);
    }

    @Override
    public void stop() {
        clientSessionCloseListener.close();
    }
}

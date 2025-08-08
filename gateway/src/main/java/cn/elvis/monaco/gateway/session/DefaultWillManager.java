package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.ChannelKeys;
import cn.elvis.monaco.gateway.entity.WillMessage;
import cn.elvis.monaco.gateway.listener.ClientSessionCloseListener;
import io.netty.util.HashedWheelTimer;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class DefaultWillManager implements WillManager {

    private final Map<String, WillMessage> store = new ConcurrentHashMap<>();

    private final HashedWheelTimer timer = new HashedWheelTimer();

    private final ClientSessionCloseListener clientSessionCloseListener;

    public DefaultWillManager(EventBus eventBus) {
        this.clientSessionCloseListener = new ClientSessionCloseListener(eventBus, clientId ->
                Optional.ofNullable(store.get(clientId)).ifPresent(willMessage ->
                        timer.newTimeout(timeout -> {
                            if (timeout.isExpired() || timeout.isCancelled()) {
                                store.remove(clientId);
                                return;
                            }
                            if (willMessage.expiryTime().isAfter(Instant.now())) {
                                eventBus.publish(ChannelKeys.WILL_MESSAGE_PUBLISH_CHANNEL, willMessage);
                                store.remove(clientId);
                            }
                        }, willMessage.delayInterval().getSeconds(), TimeUnit.SECONDS)));
    }

    @Override
    public void addWill(String clientId, WillMessage willMessage) {
        store.put(clientId, willMessage);
    }

    @Override
    public void stop() {
        clientSessionCloseListener.close();
    }
}

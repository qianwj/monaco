package cn.elvis.monaco.gateway.listener;

import cn.elvis.monaco.gateway.ChannelKeys;
import cn.elvis.monaco.gateway.entity.events.SubscriptionExtend;
import io.vertx.core.eventbus.EventBus;

import java.util.function.Consumer;

public final class ClientSessionSubscribeListener extends EventListener<SubscriptionExtend> {

    public ClientSessionSubscribeListener(EventBus eventBus, Consumer<SubscriptionExtend> consumer) {
        super(eventBus, ChannelKeys.CLIENT_SESSION_SUBSCRIBE, consumer);
    }
}

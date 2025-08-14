package cn.elvis.monaco.listener;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.events.SubscriptionExtend;
import io.vertx.core.eventbus.EventBus;

import java.util.function.Consumer;

public final class ClientSessionSubscribeListener extends EventListener<SubscriptionExtend> {

    public ClientSessionSubscribeListener(EventBus eventBus, Consumer<SubscriptionExtend> consumer) {
        super(eventBus, ChannelKeys.CLIENT_SESSION_SUBSCRIBE, consumer);
    }
}

package cn.elvis.monaco.gateway.listener;

import cn.elvis.monaco.gateway.ChannelKeys;
import io.vertx.core.eventbus.EventBus;

import java.util.function.Consumer;

public final class ClientSessionCloseListener extends EventListener<String> {

    public ClientSessionCloseListener(EventBus eventBus, Consumer<String> consumer) {
        super(eventBus, ChannelKeys.CLIENT_SESSION_CLOSE, consumer);
    }
}

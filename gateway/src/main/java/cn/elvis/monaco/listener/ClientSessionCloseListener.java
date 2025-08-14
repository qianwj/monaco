package cn.elvis.monaco.listener;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.events.ClientSessionClose;
import io.vertx.core.eventbus.EventBus;

import java.util.function.Consumer;

public final class ClientSessionCloseListener extends EventListener<ClientSessionClose> {

    public ClientSessionCloseListener(EventBus eventBus, Consumer<ClientSessionClose> consumer) {
        super(eventBus, ChannelKeys.CLIENT_SESSION_CLOSE, consumer);
    }
}

package cn.elvis.monaco.listener;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.WillMessage;
import io.vertx.core.eventbus.EventBus;

import java.util.function.Consumer;

public final class WillPublishListener extends EventListener<WillMessage> {

    public WillPublishListener(EventBus eventBus, Consumer<WillMessage> consumer) {
        super(eventBus, ChannelKeys.WILL_MESSAGE_PUBLISH_CHANNEL, consumer);
    }
}

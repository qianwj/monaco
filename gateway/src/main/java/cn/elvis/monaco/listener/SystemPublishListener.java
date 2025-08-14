package cn.elvis.monaco.listener;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.PublishMessage;
import io.vertx.core.eventbus.EventBus;

import java.util.function.Consumer;

public final class SystemPublishListener extends EventListener<PublishMessage> {

    public SystemPublishListener(EventBus eventBus, Consumer<PublishMessage> consumer) {
        super(eventBus, ChannelKeys.MESSAGE_PUBLISH_CHANNEL, consumer);
    }
}

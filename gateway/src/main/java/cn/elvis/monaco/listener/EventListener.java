package cn.elvis.monaco.listener;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.util.function.Consumer;

public abstract class EventListener<T> {

    private static final Logger log = LoggerFactory.getLogger(EventListener.class);

    final MessageConsumer<T> consumer;

    protected EventListener(EventBus eventBus, String channel, Consumer<T> consumer) {
        this.consumer = eventBus.consumer(channel, packet -> {
            log.info("Received channel(" + channel + ") event: " + packet.body());
            consumer.accept(packet.body());
        });
    }

    public final void close() {
        consumer.unregister();
    }
}

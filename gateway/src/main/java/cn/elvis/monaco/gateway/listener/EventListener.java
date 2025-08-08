package cn.elvis.monaco.gateway.listener;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;

import java.util.function.Consumer;

public abstract class EventListener<T> {

    final MessageConsumer<T> consumer;

    protected EventListener(EventBus eventBus, String channel, Consumer<T> consumer) {
        this.consumer = eventBus.consumer(channel, packet -> consumer.accept(packet.body()));
    }

    public final void close() {
        consumer.unregister();
    }
}

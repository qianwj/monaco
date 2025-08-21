package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.store.MessageStore;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class MemoryMessageStore implements MessageStore {

    private final Map<String, Queue<PublishMessage>> store = new ConcurrentHashMap<>();

    @Override
    public void push(String clientId, PublishMessage publishMessage) {
        store.compute(clientId, (k, queue) -> {
           queue = Optional.ofNullable(queue).orElse(new ConcurrentLinkedQueue<>());
           queue.offer(publishMessage);
           return queue;
        });
    }

    @Override
    public Optional<PublishMessage> poll(String clientId) {
        return Optional.ofNullable(store.get(clientId)).flatMap(queue -> Optional.ofNullable(queue.poll()));
    }
}

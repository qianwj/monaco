package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.store.MessageStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class MemoryMessageStore implements MessageStore {

    private final Map<String, Queue<PublishMessage>> store = new ConcurrentHashMap<>();

    private final Map<Integer, Set<String>> messageReceivers = new ConcurrentHashMap<>();

    @Override
    public void push(String clientId, PublishMessage publishMessage) {
        store.compute(clientId, (k, queue) -> {
           queue = Optional.ofNullable(queue).orElse(new ConcurrentLinkedQueue<>());
           queue.offer(publishMessage);
           return queue;
        });
        messageReceivers.compute(publishMessage.packetId(), (k, receivers) -> {
            if (receivers == null) {
                receivers = new HashSet<>();
            }
            receivers.add(clientId);
            return receivers;
        });
    }

    @Override
    public Optional<PublishMessage> poll(String clientId) {
        return Optional.ofNullable(store.get(clientId)).flatMap(queue -> Optional.ofNullable(queue.poll()));
    }

    @Override
    public Set<String> getReceivers(int packetId) {
        return messageReceivers.getOrDefault(packetId, new HashSet<>());
    }
}

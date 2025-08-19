package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.store.RetainMessageStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryRetainMessageStore implements RetainMessageStore {

    private final Map<String, PublishMessage> store = new ConcurrentHashMap<>();

    @Override
    public void setRetain(PublishMessage message) {
        store.put(message.topic(), message);
    }

    @Override
    public void removeRetain(String topic) {
        store.remove(topic);
    }

}

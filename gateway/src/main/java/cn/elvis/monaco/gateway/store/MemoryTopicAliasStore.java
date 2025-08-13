package cn.elvis.monaco.gateway.store;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stored topic alias
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class MemoryTopicAliasStore implements TopicAliasStore {

    private final Map<String, Map<Integer, String>> store = new ConcurrentHashMap<>();

    private final Map<String, Integer> topicAliasMaximumStore = new ConcurrentHashMap<>();

    @Override
    public void setTopicAliasMaximum(String clientId, int topicAliasMaximum) {
        if (topicAliasMaximum < 1) {
            return;
        }
        topicAliasMaximumStore.put(clientId, topicAliasMaximum);
    }

    @Override
    public void addTopicAlias(String clientId, String topic, int topicAlias) {
        if (topicAlias == 0) {
            throw new IllegalArgumentException("topicAlias must not be zero");
        }
        if (topicAlias > topicAliasMaximumStore.getOrDefault(clientId, 0)) {
            return;
        }
        Map<Integer, String> clientTopicAlias = store.getOrDefault(clientId, new ConcurrentHashMap<>());
        clientTopicAlias.put(topicAlias, topic);
        store.put(clientId, clientTopicAlias);
    }

    public String getTopic(String clientId, int topicAlias) {
        return Optional.ofNullable(store.get(clientId))
                .flatMap(clientTopicAlias -> Optional.ofNullable(clientTopicAlias.get(topicAlias)))
                .orElse(null);
    }

    @Override
    public void clearTopicAlias(String clientId) {
        store.remove(clientId);
        topicAliasMaximumStore.remove(clientId);
    }
}

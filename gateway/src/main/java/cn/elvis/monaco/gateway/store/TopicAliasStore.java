package cn.elvis.monaco.gateway.store;

/**
 * Topic alias store
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface TopicAliasStore {

    void setTopicAliasMaximum(String clientId, int topicAliasMaximum);

    void addTopicAlias(String clientId, String topic, int topicAlias);

    String getTopic(String clientId, int topicAlias);

    void clearTopicAlias(String clientId);
}

package cn.elvis.monaco.store;

/**
 * Topic alias store
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface TopicAliasStore {

    void setTopicAliasMaximum(String clientId, int topicAliasMaximum);

    int topicAliasMaximum(String clientId);

    void addTopicAlias(String clientId, String topic, int topicAlias);

    String getTopic(String clientId, int topicAlias);

    void clearTopicAlias(String clientId);
}

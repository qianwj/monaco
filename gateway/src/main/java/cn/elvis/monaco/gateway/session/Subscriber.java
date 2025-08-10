package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.entity.Subscription;

public interface Subscriber {

    void subscribe(Subscription subscription);

    void unsubscribe(String topicFilter);

    boolean isReSubscribed(String topicFilter);
}

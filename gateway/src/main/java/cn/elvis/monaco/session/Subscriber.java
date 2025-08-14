package cn.elvis.monaco.session;

import cn.elvis.monaco.entity.Subscription;

public interface Subscriber {

    void subscribe(Subscription subscription);

    void unsubscribe(String topicFilter);

    boolean isReSubscribed(String topicFilter);
}

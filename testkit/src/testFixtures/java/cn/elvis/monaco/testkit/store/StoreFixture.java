package cn.elvis.monaco.testkit.store;

import cn.elvis.monaco.core.port.BrokerStore;

public interface StoreFixture extends AutoCloseable {

    BrokerStore store();

    BrokerStore restart();

    void crash();

    @Override
    void close();
}

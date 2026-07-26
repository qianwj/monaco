package cn.elvis.monaco.testkit.store;

public interface StoreFixtureProvider {

    String name();

    StoreCapabilities capabilities();

    StoreFixture create(StoreTestDirectory directory);
}

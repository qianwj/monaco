package cn.elvis.monaco.plugin.contract;

import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.api.lifecycle.MonacoPluginFactory;
import cn.elvis.monaco.plugin.example.ExamplePluginFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceLoaderContractTest {

    @BeforeEach
    void resetCounters() {
        ExamplePluginFactory.reset();
    }

    @Test
    void discoversFactoryAndKeepsLifecycleLazy() {
        MonacoPluginFactory factory = ServiceLoader.load(MonacoPluginFactory.class).findFirst().orElseThrow();
        MonacoPlugin plugin = factory.create();

        var start = plugin.start(null);
        assertEquals(0, ExamplePluginFactory.startCount());
        StepVerifier.create(start).verifyComplete();
        assertEquals(1, ExamplePluginFactory.startCount());

        var stop = plugin.stop();
        assertEquals(0, ExamplePluginFactory.stopCount());
        StepVerifier.create(stop).verifyComplete();
        assertEquals(1, ExamplePluginFactory.stopCount());
        assertEquals("example-plugin", plugin.descriptor().id());
    }
}

package cn.elvis.monaco.plugin.runtime.classloading;

import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.runtime.PluginRuntime;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginClassLoaderTest {

    @Test
    void exposesSharedApiButHidesBrokerInternals() throws Exception {
        try (PluginClassLoader loader = new PluginClassLoader(
                new URL[0], PluginClassLoaderTest.class.getClassLoader())) {
            assertSame(MonacoPlugin.class, loader.loadClass(MonacoPlugin.class.getName()));
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass(PluginRuntime.class.getName()));
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass("cn.elvis.monaco.core.port.BrokerStore"));
        }
    }
}

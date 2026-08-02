package cn.elvis.monaco.plugin.runtime.source;

import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.api.lifecycle.MonacoPluginFactory;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.classloading.PluginClassLoader;

import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Instantiates exactly one local factory from a plugin-owned class loader. */
public final class ServiceLoaderSource {

    public MonacoPlugin load(PluginClassLoader classLoader) {
        try {
            List<MonacoPluginFactory> factories = ServiceLoader
                    .load(MonacoPluginFactory.class, classLoader)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
            if (factories.size() != 1) {
                throw new PluginRuntimeException(
                        "Plugin must provide exactly one MonacoPluginFactory, found " + factories.size());
            }
            MonacoPluginFactory factory = factories.getFirst();
            if (factory.getClass().getClassLoader() != classLoader) {
                throw new PluginRuntimeException("Plugin factory was loaded by the parent ClassLoader");
            }
            MonacoPlugin plugin = factory.create();
            if (plugin == null) {
                throw new PluginRuntimeException("Plugin factory returned null");
            }
            return plugin;
        } catch (PluginRuntimeException exception) {
            throw exception;
        } catch (ServiceConfigurationError | RuntimeException exception) {
            throw new PluginRuntimeException("Cannot instantiate plugin ServiceLoader provider", exception);
        }
    }
}

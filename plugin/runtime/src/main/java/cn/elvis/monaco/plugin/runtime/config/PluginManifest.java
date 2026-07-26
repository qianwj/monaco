package cn.elvis.monaco.plugin.runtime.config;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.descriptor.PluginCapability;
import cn.elvis.monaco.plugin.api.descriptor.PluginDependency;
import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;

import java.util.List;
import java.util.Set;

/** Validated code identity read from {@code plugin.yaml}. */
public record PluginManifest(
        String id,
        String name,
        String version,
        ApiVersion apiVersion,
        Set<PluginCapability> capabilities,
        List<PluginDependency> dependencies
) {

    public PluginManifest {
        PluginDescriptor descriptor = new PluginDescriptor(
                id, name, version, apiVersion, capabilities, dependencies);
        id = descriptor.id();
        name = descriptor.name();
        version = descriptor.version();
        apiVersion = descriptor.apiVersion();
        capabilities = descriptor.capabilities();
        dependencies = descriptor.dependencies();
    }

    public PluginDescriptor descriptor() {
        return new PluginDescriptor(id, name, version, apiVersion, capabilities, dependencies);
    }
}

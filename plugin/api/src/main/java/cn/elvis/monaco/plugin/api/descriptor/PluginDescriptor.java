package cn.elvis.monaco.plugin.api.descriptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable code identity and declared capabilities of a Monaco plugin. */
public record PluginDescriptor(
        String id,
        String name,
        String version,
        ApiVersion apiVersion,
        Set<PluginCapability> capabilities,
        List<PluginDependency> dependencies
) {

    private static final Pattern ID_PATTERN =
            Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*");
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)"
                    + "(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?"
                    + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

    public PluginDescriptor {
        id = validatePluginId(id);
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("Plugin name must contain 1-128 characters");
        }
        name = name.trim();
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("Plugin version must use SemVer: " + version);
        }
        if (apiVersion == null) {
            throw new IllegalArgumentException("Plugin API version must not be null");
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);

        Set<String> dependencyIds = new HashSet<>();
        for (PluginDependency dependency : dependencies) {
            if (dependency == null) {
                throw new IllegalArgumentException("Plugin dependencies must not contain null");
            }
            if (id.equals(dependency.pluginId())) {
                throw new IllegalArgumentException("Plugin must not depend on itself: " + id);
            }
            if (!dependencyIds.add(dependency.pluginId())) {
                throw new IllegalArgumentException("Duplicate plugin dependency: " + dependency.pluginId());
            }
        }
    }

    static String validatePluginId(String value) {
        if (value == null || !ID_PATTERN.matcher(value).matches() || value.length() > 128) {
            throw new IllegalArgumentException(
                    "Plugin id must be a lowercase dotted or dashed identifier of at most 128 characters: " + value);
        }
        return value;
    }
}

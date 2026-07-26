package cn.elvis.monaco.plugin.api.descriptor;

/**
 * Declares an ordering and availability dependency on another plugin.
 * Version constraints use a runtime-defined SemVer range syntax; {@code *}
 * accepts any version.
 */
public record PluginDependency(String pluginId, String versionConstraint) {

    public static final String ANY_VERSION = "*";

    public PluginDependency {
        pluginId = PluginDescriptor.validatePluginId(pluginId);
        if (versionConstraint == null || versionConstraint.isBlank()) {
            throw new IllegalArgumentException("Plugin dependency version constraint must not be blank");
        }
        versionConstraint = versionConstraint.trim();
    }

    public PluginDependency(String pluginId) {
        this(pluginId, ANY_VERSION);
    }
}

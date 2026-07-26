package cn.elvis.monaco.plugin.api.context;

import java.util.Map;
import java.util.Set;

/** Authenticated identity returned by a plugin. */
public record PluginPrincipal(
        String name,
        Set<String> roles,
        Map<String, String> attributes
) {

    public PluginPrincipal {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Principal name must not be blank");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String toString() {
        return "PluginPrincipal[name=" + name + ", roles=" + roles
                + ", attributeKeys=" + attributes.keySet() + ']';
    }
}

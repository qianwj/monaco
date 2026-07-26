package cn.elvis.monaco.plugin.contract;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.descriptor.PluginCapability;
import cn.elvis.monaco.plugin.api.descriptor.PluginDependency;
import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorContractTest {

    @Test
    void parsesAndComparesApiVersions() {
        assertEquals(new ApiVersion(1, 0), ApiVersion.parse("1"));
        assertEquals(new ApiVersion(1, 3), ApiVersion.parse("1.3"));
        assertTrue(new ApiVersion(1, 3).supports(new ApiVersion(1, 2)));
        assertFalse(new ApiVersion(1, 2).supports(new ApiVersion(1, 3)));
        assertFalse(new ApiVersion(2, 0).supports(new ApiVersion(1, 9)));
    }

    @Test
    void rejectsInvalidApiVersions() {
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("1.2.3"));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("one"));
    }

    @Test
    void descriptorDefensivelyCopiesCollections() {
        Set<PluginCapability> capabilities = new HashSet<>();
        capabilities.add(PluginCapability.AUTHENTICATION);
        List<PluginDependency> dependencies = new ArrayList<>();
        dependencies.add(new PluginDependency("base-auth", ">=1.0.0"));

        PluginDescriptor descriptor = new PluginDescriptor(
                "example.auth",
                "Example Auth",
                "1.2.3",
                ApiVersion.CURRENT,
                capabilities,
                dependencies);

        capabilities.clear();
        dependencies.clear();
        assertEquals(Set.of(PluginCapability.AUTHENTICATION), descriptor.capabilities());
        assertEquals(List.of(new PluginDependency("base-auth", ">=1.0.0")), descriptor.dependencies());
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.capabilities().add(PluginCapability.AUTHORIZATION));
    }

    @Test
    void rejectsInvalidIdentityAndDependencies() {
        assertThrows(IllegalArgumentException.class, () -> descriptor("UpperCase", "1.0.0", List.of()));
        assertThrows(IllegalArgumentException.class, () -> descriptor("valid-id", "1", List.of()));
        assertThrows(IllegalArgumentException.class, () -> descriptor("valid-id", "1.0.0-..", List.of()));
        assertThrows(IllegalArgumentException.class, () -> descriptor("valid-id", "01.0.0", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> descriptor("valid-id", "1.0.0", List.of(new PluginDependency("valid-id"))));
        assertThrows(IllegalArgumentException.class,
                () -> descriptor("valid-id", "1.0.0",
                        List.of(new PluginDependency("dependency"), new PluginDependency("dependency"))));
    }

    private static PluginDescriptor descriptor(
            String id,
            String version,
            List<PluginDependency> dependencies
    ) {
        return new PluginDescriptor(id, "Plugin", version, ApiVersion.CURRENT,
                Set.of(PluginCapability.EVENT_LISTENER), dependencies);
    }
}

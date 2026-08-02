package cn.elvis.monaco.plugin.runtime.config;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.descriptor.PluginCapability;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestParserTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesValidatedManifest() throws Exception {
        Path manifest = write("""
                id: acl-file
                name: File ACL
                version: 1.2.3
                apiVersion: "1"
                capabilities:
                  - authentication
                  - authorization
                dependencies:
                  - id: identity
                    version: ">=1.0.0 <2.0.0"
                """);

        PluginManifest parsed = new ManifestParser(PluginRuntimeConfig.defaults(temporaryDirectory))
                .parse(manifest);

        assertEquals("acl-file", parsed.id());
        assertEquals(ApiVersion.CURRENT, parsed.apiVersion());
        assertEquals(
                java.util.Set.of(PluginCapability.AUTHENTICATION, PluginCapability.AUTHORIZATION),
                parsed.capabilities());
        assertEquals("identity", parsed.dependencies().getFirst().pluginId());
    }

    @Test
    void rejectsUnknownAndDuplicateFields() throws Exception {
        Path unknown = write("""
                id: sample
                name: Sample
                version: 1.0.0
                apiVersion: "1"
                capabilities: []
                dependencies: []
                required: true
                """);
        ManifestParser parser = new ManifestParser(PluginRuntimeConfig.defaults(temporaryDirectory));

        assertThrows(PluginRuntimeException.class, () -> parser.parse(unknown));

        Path duplicateCapability = write("""
                id: sample
                name: Sample
                version: 1.0.0
                apiVersion: "1"
                capabilities: [authentication, authentication]
                dependencies: []
                """);
        assertThrows(PluginRuntimeException.class, () -> parser.parse(duplicateCapability));
    }

    private Path write(String content) throws Exception {
        Path manifest = temporaryDirectory.resolve("plugin-" + System.nanoTime() + ".yaml");
        Files.writeString(manifest, content);
        return manifest;
    }
}

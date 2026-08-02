package cn.elvis.monaco.plugin.runtime.source;

import cn.elvis.monaco.plugin.api.lifecycle.MonacoPluginFactory;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.config.PluginRuntimeConfig;
import example.plugin.fixture.FixturePluginFactory;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import cn.elvis.monaco.plugin.runtime.spi.PluginCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectoryPluginSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsExactlyOneFactoryFromPluginClassLoader() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("valid"));
        Path plugin = Files.createDirectory(root.resolve("fixture"));
        writeManifest(plugin);
        writeFixtureJar(plugin.resolve("plugin.jar"));
        DirectoryPluginSource source = new DirectoryPluginSource(
                PluginRuntimeConfig.defaults(root), PluginTelemetry.noop());

        List<PluginCandidate> handles = source.load().collectList().block(Duration.ofSeconds(5));

        assertEquals(1, handles.size());
        assertEquals("fixture", handles.getFirst().descriptor().id());
        handles.getFirst().close().block();
        source.close().block();
    }

    @Test
    void rejectsJarThatDuplicatesPluginApi() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("duplicate-api"));
        Path plugin = Files.createDirectory(root.resolve("fixture"));
        writeManifest(plugin);
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(plugin.resolve("plugin.jar")))) {
            jar.putNextEntry(new JarEntry("cn/elvis/monaco/plugin/api/Duplicate.class"));
            jar.write(new byte[]{0});
            jar.closeEntry();
        }
        DirectoryPluginSource source = new DirectoryPluginSource(
                PluginRuntimeConfig.defaults(root), PluginTelemetry.noop());

        StepVerifier.create(source.load())
                .expectError(PluginRuntimeException.class)
                .verify();
        source.close().block();
    }

    private static void writeManifest(Path plugin) throws Exception {
        Files.writeString(plugin.resolve("plugin.yaml"), """
                id: fixture
                name: Fixture Plugin
                version: 1.0.0
                apiVersion: "1"
                capabilities: []
                dependencies: []
                """);
    }

    private static void writeFixtureJar(Path destination) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(destination))) {
            addClass(jar, FixturePluginFactory.class);
            Class<?> pluginClass = Class.forName("example.plugin.fixture.FixturePlugin");
            addClass(jar, pluginClass);
            jar.putNextEntry(new JarEntry(
                    "META-INF/services/" + MonacoPluginFactory.class.getName()));
            jar.write((FixturePluginFactory.class.getName() + System.lineSeparator())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private static void addClass(JarOutputStream jar, Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        jar.putNextEntry(new JarEntry(resource));
        try (InputStream input = type.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing test class resource: " + resource);
            }
            input.transferTo(jar);
        }
        jar.closeEntry();
    }
}

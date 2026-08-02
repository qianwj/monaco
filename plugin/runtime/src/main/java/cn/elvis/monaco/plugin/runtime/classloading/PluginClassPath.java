package cn.elvis.monaco.plugin.runtime.classloading;

import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.config.PluginRuntimeConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves a plugin directory without following package-internal links. */
public final class PluginClassPath {

    private PluginClassPath() {
    }

    public static PluginBundle resolve(
            Path pluginsRoot,
            Path pluginDirectory,
            PluginRuntimeConfig config
    ) {
        try {
            Path root = pluginsRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path directory = pluginDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!directory.startsWith(root)
                    || Files.isSymbolicLink(pluginDirectory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new PluginRuntimeException("Plugin directory escapes configured root: " + pluginDirectory);
            }

            Path manifest = regularFile(directory.resolve("plugin.yaml"), directory, false);
            Path mainJar = regularFile(directory.resolve("plugin.jar"), directory, true);
            List<Path> artifacts = new ArrayList<>();
            artifacts.add(mainJar);

            Path lib = directory.resolve("lib");
            if (Files.exists(lib, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(lib) || !Files.isDirectory(lib, LinkOption.NOFOLLOW_LINKS)) {
                    throw new PluginRuntimeException("Plugin lib path must be a regular directory: " + lib);
                }
                try (var entries = Files.list(lib)) {
                    entries.filter(path -> path.getFileName().toString().endsWith(".jar"))
                            .sorted()
                            .forEach(path -> artifacts.add(regularFile(path, directory, true)));
                }
            }

            if (artifacts.size() > config.maxJarsPerPlugin()) {
                throw new PluginRuntimeException("Plugin contains too many JARs: " + directory);
            }
            Set<Path> unique = new HashSet<>();
            for (Path artifact : artifacts) {
                if (!unique.add(artifact)) {
                    throw new PluginRuntimeException("Plugin contains a duplicate JAR: " + artifact);
                }
                long size = Files.size(artifact);
                if (size < 1 || size > config.maxJarBytes()) {
                    throw new PluginRuntimeException("Plugin JAR size is outside the configured limit: " + artifact);
                }
            }
            return new PluginBundle(directory, manifest, artifacts);
        } catch (PluginRuntimeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PluginRuntimeException("Cannot resolve plugin package: " + pluginDirectory, exception);
        }
    }

    private static Path regularFile(Path candidate, Path pluginDirectory, boolean jar) {
        try {
            if (Files.isSymbolicLink(candidate)
                    || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new PluginRuntimeException("Required plugin file is missing or linked: " + candidate);
            }
            Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.startsWith(pluginDirectory)) {
                throw new PluginRuntimeException("Plugin file escapes package directory: " + candidate);
            }
            if (jar && !real.getFileName().toString().endsWith(".jar")) {
                throw new PluginRuntimeException("Plugin artifact must use .jar suffix: " + candidate);
            }
            return real;
        } catch (IOException exception) {
            throw new PluginRuntimeException("Cannot resolve plugin file: " + candidate, exception);
        }
    }
}

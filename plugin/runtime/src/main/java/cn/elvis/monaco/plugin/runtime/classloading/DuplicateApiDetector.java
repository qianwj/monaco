package cn.elvis.monaco.plugin.runtime.classloading;

import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Rejects plugin packages that shade runtime-owned public contracts. */
public final class DuplicateApiDetector {

    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "cn/elvis/monaco/plugin/api/",
            "cn/elvis/monaco/plugin/runtime/",
            "cn/elvis/monaco/protocol/",
            "cn/elvis/monaco/core/",
            "cn/elvis/monaco/runtime/",
            "cn/elvis/monaco/store/",
            "cn/elvis/monaco/broker/",
            "reactor/core/",
            "org/reactivestreams/");

    public void verify(List<Path> artifacts) {
        for (Path artifact : artifacts) {
            verify(artifact);
        }
    }

    private static void verify(Path artifact) {
        try (JarFile jar = new JarFile(artifact.toFile(), false)) {
            jar.stream().map(java.util.jar.JarEntry::getName)
                    .filter(name -> name.endsWith(".class"))
                    .filter(DuplicateApiDetector::forbidden)
                    .findFirst()
                    .ifPresent(name -> {
                        throw new PluginRuntimeException(
                                "Plugin JAR duplicates a runtime-owned API class: " + artifact + "!/" + name);
                    });
        } catch (PluginRuntimeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PluginRuntimeException("Cannot inspect plugin JAR: " + artifact, exception);
        }
    }

    private static boolean forbidden(String entry) {
        return FORBIDDEN_PREFIXES.stream().anyMatch(entry::startsWith);
    }
}

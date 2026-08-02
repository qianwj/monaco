package cn.elvis.monaco.plugin.runtime.classloading;

import java.nio.file.Path;
import java.util.List;

/** Validated local paths for one plugin package. */
public record PluginBundle(Path directory, Path manifest, List<Path> artifacts) {

    public PluginBundle {
        if (directory == null || manifest == null || artifacts == null || artifacts.isEmpty()) {
            throw new IllegalArgumentException("Plugin bundle paths must be complete");
        }
        artifacts = List.copyOf(artifacts);
    }
}

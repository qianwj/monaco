package cn.elvis.monaco.plugin.runtime.catalog;

import cn.elvis.monaco.plugin.api.descriptor.PluginDependency;
import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Stable SHA-256 identity used for cluster readiness checks. */
public record PluginFingerprint(String sha256) {

    public PluginFingerprint {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Plugin fingerprint must be a lowercase SHA-256 value");
        }
    }

    public static PluginFingerprint calculate(
            PluginDescriptor descriptor,
            List<Path> artifacts,
            Map<String, String> semanticConfig
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, descriptor.apiVersion().major() + "." + descriptor.apiVersion().minor());
            update(digest, descriptor.id());
            update(digest, descriptor.version());
            descriptor.capabilities().stream().map(Enum::name).sorted()
                    .forEach(value -> update(digest, value));
            descriptor.dependencies().stream()
                    .sorted(java.util.Comparator.comparing(PluginDependency::pluginId))
                    .forEach(dependency -> {
                        update(digest, dependency.pluginId());
                        update(digest, dependency.versionConstraint());
                    });
            semanticConfig.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        update(digest, entry.getKey());
                        update(digest, entry.getValue());
                    });
            artifacts.stream().sorted(java.util.Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> updateArtifact(digest, path));
            return new PluginFingerprint(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateArtifact(MessageDigest digest, Path path) {
        update(digest, path.getFileName().toString());
        byte[] buffer = new byte[16 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            digest.update((byte) 0);
        } catch (IOException exception) {
            throw new PluginRuntimeException("Cannot fingerprint plugin artifact: " + path, exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}

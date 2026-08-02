package cn.elvis.monaco.plugin.runtime.catalog;

import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable-order registry of every plugin loaded for one runtime generation. */
public final class PluginCatalog {

    private volatile Map<String, PluginHandle> handles = Map.of();

    public synchronized void replace(List<PluginHandle> orderedHandles) {
        LinkedHashMap<String, PluginHandle> next = new LinkedHashMap<>();
        for (PluginHandle handle : orderedHandles) {
            if (next.putIfAbsent(handle.descriptor().id(), handle) != null) {
                throw new PluginRuntimeException("Duplicate plugin id: " + handle.descriptor().id());
            }
        }
        handles = java.util.Collections.unmodifiableMap(next);
    }

    public List<PluginHandle> handles() {
        return List.copyOf(handles.values());
    }

    public Optional<PluginHandle> find(String pluginId) {
        return Optional.ofNullable(handles.get(pluginId));
    }

    public boolean requiredPluginsActive() {
        return handles.values().stream()
                .filter(handle -> handle.deployment().required())
                .allMatch(handle -> handle.state() == PluginState.ACTIVE);
    }

    public String requiredFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            handles.values().stream()
                    .filter(handle -> handle.deployment().required())
                    .sorted(java.util.Comparator.comparing(handle -> handle.descriptor().id()))
                    .forEach(handle -> {
                        digest.update(handle.descriptor().id().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(handle.fingerprint().sha256().getBytes(StandardCharsets.US_ASCII));
                        digest.update((byte) 0);
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public synchronized void clear() {
        handles = Map.of();
    }
}

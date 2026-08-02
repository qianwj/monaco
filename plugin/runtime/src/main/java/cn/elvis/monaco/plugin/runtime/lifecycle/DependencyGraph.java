package cn.elvis.monaco.plugin.runtime.lifecycle;

import cn.elvis.monaco.plugin.api.descriptor.PluginDependency;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Validates dependencies and produces a deterministic topological order. */
public final class DependencyGraph {

    public List<PluginHandle> order(List<PluginHandle> handles) {
        Map<String, PluginHandle> byId = new LinkedHashMap<>();
        for (PluginHandle handle : handles) {
            PluginHandle duplicate = byId.putIfAbsent(handle.descriptor().id(), handle);
            if (duplicate != null) {
                throw new PluginRuntimeException("Duplicate plugin id: " + handle.descriptor().id());
            }
        }

        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        byId.keySet().forEach(id -> {
            indegree.put(id, 0);
            dependents.put(id, new ArrayList<>());
        });
        for (PluginHandle handle : handles) {
            for (PluginDependency dependency : handle.descriptor().dependencies()) {
                PluginHandle required = byId.get(dependency.pluginId());
                if (required == null) {
                    throw new PluginRuntimeException(
                            "Missing dependency " + dependency.pluginId() + " for " + handle.descriptor().id());
                }
                if (!VersionConstraint.matches(
                        dependency.versionConstraint(), required.descriptor().version())) {
                    throw new PluginRuntimeException(
                            "Dependency version mismatch for " + handle.descriptor().id()
                                    + ": " + dependency.pluginId() + ' ' + dependency.versionConstraint());
                }
                indegree.compute(handle.descriptor().id(), (ignored, value) -> value + 1);
                dependents.get(dependency.pluginId()).add(handle.descriptor().id());
            }
        }

        java.util.Comparator<PluginHandle> stableOrder = java.util.Comparator
                .comparingInt((PluginHandle handle) -> handle.deployment().priority())
                .thenComparing(handle -> handle.descriptor().id());
        PriorityQueue<PluginHandle> ready = new PriorityQueue<>(stableOrder);
        handles.stream()
                .filter(handle -> indegree.get(handle.descriptor().id()) == 0)
                .forEach(ready::add);

        List<PluginHandle> ordered = new ArrayList<>(handles.size());
        while (!ready.isEmpty()) {
            PluginHandle handle = ready.remove();
            ordered.add(handle);
            for (String dependentId : dependents.get(handle.descriptor().id())) {
                int remaining = indegree.compute(dependentId, (ignored, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(byId.get(dependentId));
                }
            }
        }
        if (ordered.size() != handles.size()) {
            List<String> cycle = indegree.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            throw new PluginRuntimeException("Plugin dependency cycle: " + cycle);
        }
        return List.copyOf(ordered);
    }
}

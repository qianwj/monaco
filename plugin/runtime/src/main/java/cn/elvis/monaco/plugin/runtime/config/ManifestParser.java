package cn.elvis.monaco.plugin.runtime.config;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.descriptor.PluginCapability;
import cn.elvis.monaco.plugin.api.descriptor.PluginDependency;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict and size-bounded {@code plugin.yaml} parser. */
public final class ManifestParser {

    private final PluginRuntimeConfig config;
    private final ObjectMapper mapper;

    public ManifestParser(PluginRuntimeConfig config) {
        this.config = config;

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setMaxAliasesForCollections(config.maxYamlAliases());
        loaderOptions.setNestingDepthLimit(config.maxManifestDepth());
        loaderOptions.setCodePointLimit((int) Math.min(Integer.MAX_VALUE, config.maxManifestBytes()));

        YAMLFactory yamlFactory = YAMLFactory.builder()
                .loaderOptions(loaderOptions)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(config.maxManifestDepth())
                        .maxStringLength((int) Math.min(Integer.MAX_VALUE, config.maxManifestBytes()))
                        .build())
                .build();
        mapper = new ObjectMapper(yamlFactory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public PluginManifest parse(Path manifestPath) {
        Path path = manifestPath.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
                throw new PluginRuntimeException("Plugin manifest must be a regular non-link file: " + path);
            }
            long size = Files.size(path);
            if (size < 1 || size > config.maxManifestBytes()) {
                throw new PluginRuntimeException("Plugin manifest size is outside the configured limit: " + path);
            }
            ManifestDocument document = mapper.readValue(Files.readAllBytes(path), ManifestDocument.class);
            return toManifest(document);
        } catch (PluginRuntimeException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new PluginRuntimeException("Invalid plugin manifest: " + path, exception);
        }
    }

    private static PluginManifest toManifest(ManifestDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Plugin manifest must not be empty");
        }
        if (document.apiVersion() == null || document.apiVersion().isBlank()) {
            throw new IllegalArgumentException("Plugin API version must not be blank");
        }
        List<String> rawCapabilities = document.capabilities() == null
                ? List.of()
                : List.copyOf(document.capabilities());
        Set<PluginCapability> capabilities = new HashSet<>();
        for (String value : rawCapabilities) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Plugin capability must not be blank");
            }
            PluginCapability capability = PluginCapability.valueOf(
                    value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
            if (!capabilities.add(capability)) {
                throw new IllegalArgumentException("Duplicate plugin capability: " + value);
            }
        }

        List<PluginDependency> dependencies = document.dependencies() == null
                ? List.of()
                : document.dependencies().stream()
                        .map(dependency -> {
                            if (dependency == null) {
                                throw new IllegalArgumentException("Plugin dependency must not be null");
                            }
                            return new PluginDependency(
                                    dependency.id(),
                                    dependency.version() == null
                                            ? PluginDependency.ANY_VERSION
                                            : dependency.version());
                        })
                        .toList();
        return new PluginManifest(
                document.id(),
                document.name(),
                document.version(),
                ApiVersion.parse(document.apiVersion()),
                Set.copyOf(capabilities),
                dependencies);
    }

    private record ManifestDocument(
            String id,
            String name,
            String version,
            String apiVersion,
            List<String> capabilities,
            List<DependencyDocument> dependencies
    ) { }

    private record DependencyDocument(String id, String version) { }
}

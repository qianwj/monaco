package cn.elvis.monaco.plugin.runtime.source;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.catalog.PluginFingerprint;
import cn.elvis.monaco.plugin.runtime.catalog.PluginState;
import cn.elvis.monaco.plugin.runtime.classloading.DuplicateApiDetector;
import cn.elvis.monaco.plugin.runtime.classloading.PluginBundle;
import cn.elvis.monaco.plugin.runtime.classloading.PluginClassLoader;
import cn.elvis.monaco.plugin.runtime.classloading.PluginClassPath;
import cn.elvis.monaco.plugin.runtime.config.ManifestParser;
import cn.elvis.monaco.plugin.runtime.config.PluginDeployment;
import cn.elvis.monaco.plugin.runtime.config.PluginManifest;
import cn.elvis.monaco.plugin.runtime.config.PluginRuntimeConfig;
import cn.elvis.monaco.plugin.runtime.invoke.LocalPluginInvoker;
import cn.elvis.monaco.plugin.runtime.spi.PluginLifecycle;
import cn.elvis.monaco.plugin.runtime.spi.PluginCandidate;
import cn.elvis.monaco.plugin.runtime.spi.PluginResource;
import cn.elvis.monaco.plugin.runtime.spi.PluginSource;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Discovers validated in-process plugins from the configured directory. */
public final class DirectoryPluginSource implements PluginSource {

    private final PluginRuntimeConfig config;
    private final ManifestParser manifestParser;
    private final DuplicateApiDetector duplicateApiDetector = new DuplicateApiDetector();
    private final ServiceLoaderSource serviceLoaderSource = new ServiceLoaderSource();
    private final PluginTelemetry telemetry;
    private final Scheduler loaderScheduler = Schedulers.newBoundedElastic(
            1, 128, "monaco-plugin-loader", 60, true);

    public DirectoryPluginSource(PluginRuntimeConfig config, PluginTelemetry telemetry) {
        if (config == null || telemetry == null) {
            throw new IllegalArgumentException("Directory plugin source components must not be null");
        }
        this.config = config;
        this.telemetry = telemetry;
        this.manifestParser = new ManifestParser(config);
    }

    @Override
    public Flux<PluginCandidate> load() {
        return Mono.fromCallable(this::discover)
                .subscribeOn(loaderScheduler)
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Void> close() {
        return Mono.fromRunnable(loaderScheduler::dispose);
    }

    private List<PluginCandidate> discover() {
        Path root = config.pluginsDirectory();
        try {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                return List.of();
            }
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new PluginRuntimeException("Plugins root must be a regular directory: " + root);
            }
            List<Path> directories;
            try (var entries = Files.list(root)) {
                directories = entries
                        .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> !Files.isSymbolicLink(path))
                        .sorted()
                        .toList();
            }
            if (directories.size() > config.maxPlugins()) {
                throw new PluginRuntimeException("Plugin count exceeds configured limit");
            }
            List<PluginCandidate> handles = new ArrayList<>();
            try {
                for (Path directory : directories) {
                    PluginCandidate handle = loadOne(root, directory);
                    if (handle != null) {
                        handles.add(handle);
                    }
                }
                return List.copyOf(handles);
            } catch (RuntimeException exception) {
                handles.forEach(handle -> handle.close().subscribe());
                throw exception;
            }
        } catch (PluginRuntimeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PluginRuntimeException("Cannot scan plugins directory: " + root, exception);
        }
    }

    private PluginCandidate loadOne(Path root, Path directory) {
        PluginBundle bundle = PluginClassPath.resolve(root, directory, config);
        PluginManifest manifest = manifestParser.parse(bundle.manifest());
        telemetry.stateChanged(manifest.id(), PluginState.DISCOVERED, PluginState.VALIDATED);
        PluginDeployment deployment = config.deploymentFor(manifest.id());
        if (!deployment.enabled()) {
            return null;
        }
        if (!ApiVersion.CURRENT.supports(manifest.apiVersion())) {
            throw new PluginRuntimeException(
                    "Unsupported plugin API version for " + manifest.id() + ": " + manifest.apiVersion());
        }
        duplicateApiDetector.verify(bundle.artifacts());

        PluginClassLoader classLoader = null;
        LocalPluginInvoker invoker = null;
        try {
            URL[] urls = bundle.artifacts().stream().map(DirectoryPluginSource::toUrl).toArray(URL[]::new);
            classLoader = new PluginClassLoader(urls, MonacoPlugin.class.getClassLoader());
            MonacoPlugin plugin = serviceLoaderSource.load(classLoader);
            List<PluginHook> hooks = PluginValidator.validate(
                    manifest,
                    plugin.descriptor(),
                    plugin.hooks());
            PluginFingerprint fingerprint = PluginFingerprint.calculate(
                    manifest.descriptor(), bundle.artifacts(), deployment.config());
            invoker = new LocalPluginInvoker(manifest.id(), deployment, telemetry, classLoader);
            return new PluginCandidate(
                    manifest.descriptor(),
                    hooks,
                    PluginLifecycle.from(plugin),
                    invoker,
                    fingerprint.sha256(),
                    classLoader,
                    PluginResource.from(classLoader));
        } catch (RuntimeException exception) {
            if (invoker != null) {
                invoker.close().subscribe();
            }
            close(classLoader);
            throw exception;
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (java.net.MalformedURLException exception) {
            throw new PluginRuntimeException("Invalid plugin JAR URL: " + path, exception);
        }
    }

    private static void close(PluginClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (IOException ignored) {
            // Preserve the original loading failure.
        }
    }
}

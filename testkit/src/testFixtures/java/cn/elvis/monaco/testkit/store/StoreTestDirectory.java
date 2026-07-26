package cn.elvis.monaco.testkit.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StoreTestDirectory implements AutoCloseable {

    private final Path path;
    private final AtomicBoolean closed = new AtomicBoolean();

    private StoreTestDirectory(Path path) {
        this.path = path;
    }

    public static StoreTestDirectory create(Path parentDirectory, String prefix) throws IOException {
        Objects.requireNonNull(parentDirectory, "parentDirectory");
        Objects.requireNonNull(prefix, "prefix");
        Files.createDirectories(parentDirectory);
        return new StoreTestDirectory(Files.createTempDirectory(parentDirectory, prefix));
    }

    public Path path() {
        return path;
    }

    public Path resolve(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path resolved = path.resolve(relativePath).normalize();
        if (!resolved.startsWith(path)) {
            throw new IllegalArgumentException("path must stay inside the store test directory");
        }
        return resolved;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true) || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path current : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }
}

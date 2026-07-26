package cn.elvis.monaco.testkit.fixture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class FixtureResourceRegistry implements AutoCloseable {

    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private boolean closed;

    public synchronized <T extends AutoCloseable> T register(T resource) {
        Objects.requireNonNull(resource, "resource");
        if (closed) {
            throw new IllegalStateException("fixture resource registry is already closed");
        }
        resources.addFirst(resource);
        return resource;
    }

    public void closeAfter(Throwable primaryFailure) {
        Objects.requireNonNull(primaryFailure, "primaryFailure");
        for (AutoCloseable resource : drainResources()) {
            try {
                resource.close();
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != primaryFailure) {
                    primaryFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        Throwable primaryFailure = null;
        for (AutoCloseable resource : drainResources()) {
            try {
                resource.close();
            } catch (Throwable cleanupFailure) {
                if (primaryFailure == null) {
                    primaryFailure = cleanupFailure;
                } else if (cleanupFailure != primaryFailure) {
                    primaryFailure.addSuppressed(cleanupFailure);
                }
            }
        }
        rethrow(primaryFailure);
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private synchronized List<AutoCloseable> drainResources() {
        if (closed) {
            return List.of();
        }
        closed = true;
        List<AutoCloseable> snapshot = new ArrayList<>(resources);
        resources.clear();
        return snapshot;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("Unexpected fixture cleanup failure", failure);
    }
}

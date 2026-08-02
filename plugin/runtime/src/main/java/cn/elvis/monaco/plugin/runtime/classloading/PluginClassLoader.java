package cn.elvis.monaco.plugin.runtime.classloading;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Child-first loader with parent-owned API and Reactor namespaces. */
public final class PluginClassLoader extends URLClassLoader {

    private static final List<String> PARENT_FIRST_PREFIXES = List.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "cn.elvis.monaco.plugin.api.",
            "cn.elvis.monaco.protocol.",
            "reactor.",
            "org.reactivestreams.");

    static {
        registerAsParallelCapable();
    }

    private final AtomicBoolean closed = new AtomicBoolean();

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (brokerInternal(name)) {
            throw new ClassNotFoundException("Broker internal class is not visible to plugins: " + name);
        }
        if (parentFirst(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException ignored) {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    @Override
    public void close() throws java.io.IOException {
        if (closed.compareAndSet(false, true)) {
            super.close();
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    private static boolean parentFirst(String name) {
        return PARENT_FIRST_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static boolean brokerInternal(String name) {
        return name.startsWith("cn.elvis.monaco.core.")
                || name.startsWith("cn.elvis.monaco.runtime.")
                || name.startsWith("cn.elvis.monaco.store.")
                || name.startsWith("cn.elvis.monaco.broker.")
                || name.startsWith("cn.elvis.monaco.gateway.")
                || name.startsWith("cn.elvis.monaco.common.")
                || name.startsWith("cn.elvis.monaco.plugin.runtime.");
    }
}

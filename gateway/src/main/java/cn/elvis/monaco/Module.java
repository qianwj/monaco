package cn.elvis.monaco;

import cn.elvis.monaco.manager.ManagerModule;
import cn.elvis.monaco.session.SessionModule;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.StoreModule;
import cn.elvis.monaco.transport.TransportModule;
import io.vertx.core.Vertx;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Define Module for manage group of objects.
 *
 * @author qianwj
 * @since  0.0.1
 */
public abstract class Module {

    protected final Vertx vertx;

    protected final Settings settings;

    protected final Map<Class<?>, Object> instances = new HashMap<>();

    protected Module(Vertx vertx, Settings settings) {
        this.vertx = vertx;
        this.settings = settings;
    }

    public abstract void init(Module... dependency);

    public <T> T instance(Class<T> clazz) {
        return Optional.ofNullable(instances.get(clazz))
                .map(clazz::cast)
                .orElse(null);
    }

    static void init(Vertx vertx, Settings settings) {
        final StoreModule store = new StoreModule(vertx, settings);
        final ManagerModule manager = new ManagerModule(vertx, settings);
        final SessionModule session = new SessionModule(vertx, settings);
        final TransportModule transport = new TransportModule(vertx, settings);
        store.init();
        manager.init(store);
        session.init(manager, store);
        transport.init(session);
    }
}

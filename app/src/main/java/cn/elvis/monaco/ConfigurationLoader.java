package cn.elvis.monaco;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

final class ConfigurationLoader {

    private volatile JsonObject cachedConfig;

    JsonObject init() {
        if (cachedConfig != null) {
            return cachedConfig;
        }
        var vertx = Vertx.vertx();
        var retriever = createConfigRetriever(vertx);
        cachedConfig = retriever.getConfig().await();
        vertx.close().await();
        return cachedConfig;
    }

    void watch(Vertx vertx) {
        var retriever = createConfigRetriever(vertx);
        retriever.listen(change -> {
            cachedConfig = change.getNewConfiguration();
            var opts = new DeliveryOptions().setLocalOnly(true);
            vertx.eventBus().publish(ChannelKeys.CONFIGURATION_CHANGE, cachedConfig, opts);
        });
    }

    private ConfigRetriever createConfigRetriever(Vertx vertx) {
        ConfigStoreOptions propsStoreOptions = new ConfigStoreOptions()
                .setType("sys")
                .setConfig(
                        new JsonObject().put("hierarchical", true)
                );
        ConfigStoreOptions fileStoreOptions = new ConfigStoreOptions()
                .setType("file")
                .setFormat("properties")
                .setConfig(
                        new JsonObject()
                                .put("path", "monaco.properties")
                                .put("hierarchical", true)
                );
        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
                .addStore(fileStoreOptions)
                .addStore(propsStoreOptions);
        return ConfigRetriever.create(vertx, options);
    }
}

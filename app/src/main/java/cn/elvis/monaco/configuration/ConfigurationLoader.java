package cn.elvis.monaco.configuration;

import cn.elvis.monaco.ChannelKeys;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

public final class ConfigurationLoader {

    public static synchronized JsonObject load(Vertx vertx) {
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
        ConfigRetriever retriever = ConfigRetriever.create(vertx, options);
//        retriever.listen(change -> {
//            // todo: compare previous config and new config
//            vertx.eventBus().publish(
//                    ChannelKeys.CONFIGURATION_CHANGE,
//                    change.getNewConfiguration(),
//                    new DeliveryOptions().setLocalOnly(true)
//            );
//        });
        return retriever.getConfig().await();
    }
}

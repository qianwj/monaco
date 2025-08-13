package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.ChannelKeys;
import cn.elvis.monaco.gateway.entity.PublishMessage;
import cn.elvis.monaco.gateway.listener.ClientSessionSubscribeListener;
import cn.elvis.monaco.gateway.manager.RetainMessageManager;
import cn.elvis.monaco.gateway.settings.Settings;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultRetainMessageManager implements RetainMessageManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultRetainMessageManager.class);

    private final Map<String, PublishMessage> store = new ConcurrentHashMap<>();

    private final boolean available;

    private ClientSessionSubscribeListener clientSessionSubscribeListener;

    public DefaultRetainMessageManager(Settings settings, EventBus eventBus) {
        this.available = settings.retainAvailable();
        if (available) {
            this.clientSessionSubscribeListener = new ClientSessionSubscribeListener(eventBus, extend -> {
                if (extend.noLocal()) {
                    log.info("No local retain message.");
                    return;
                }
                Optional.ofNullable(store.get(extend.topicFilter()))
                        .ifPresent(message -> {
                            var publishMessage = message.setRetain(extend.retainAsPublished());
                            switch (extend.retainedHandlingPolicy()) {
                                case SEND_AT_SUBSCRIBE -> eventBus.publish(ChannelKeys.MESSAGE_PUBLISH_CHANNEL, publishMessage);
                                case SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS -> {
                                    if (!extend.reSubscribe()) {
                                        eventBus.publish(ChannelKeys.MESSAGE_PUBLISH_CHANNEL, publishMessage);
                                    }
                                }
                                case DONT_SEND_AT_SUBSCRIBE -> {}
                            }
                        });
            });
        }
    }


    @Override
    public void addMessage(PublishMessage message) {
        if (available) {
            store.put(message.topic(), message);
        }
    }

    @Override
    public void close() {
        if (Objects.nonNull(clientSessionSubscribeListener)) {
            clientSessionSubscribeListener.close();
        }
    }
}

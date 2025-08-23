package cn.elvis.monaco.manager.standalone;

import cn.elvis.monaco.listener.ClientSessionSubscribeListener;
import cn.elvis.monaco.manager.RetainMessageManager;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.RetainMessageStore;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.util.Objects;

/**
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultRetainMessageManager implements RetainMessageManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultRetainMessageManager.class);

    private final RetainMessageStore store;

    private final boolean available;

    private ClientSessionSubscribeListener clientSessionSubscribeListener;

    public DefaultRetainMessageManager(Settings settings,
                                       EventBus eventBus,
                                       RetainMessageStore store) {
        this.available = settings.retainAvailable();
        if (available) {
            this.store = store;
            this.clientSessionSubscribeListener = new ClientSessionSubscribeListener(eventBus, extend -> {
                if (extend.noLocal()) {
                    log.info("No local retain message.");
                    return;
                }
//                Optional.ofNullable(store.get(extend.topicFilter()))
//                        .ifPresent(message -> {
//                            var publishMessage = message.setRetain(extend.retainAsPublished());
//                            switch (extend.retainedHandlingPolicy()) {
//                                case SEND_AT_SUBSCRIBE -> eventBus.publish(ChannelKeys.MESSAGE_PUBLISH_CHANNEL, publishMessage);
//                                case SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS -> {
//                                    if (!extend.reSubscribe()) {
//                                        eventBus.publish(ChannelKeys.MESSAGE_PUBLISH_CHANNEL, publishMessage);
//                                    }
//                                }
//                                case DONT_SEND_AT_SUBSCRIBE -> {}
//                            }
//                        });
            });
        } else {
            this.store = null;
        }
    }

    @Override
    public void shutdown() {
        if (Objects.nonNull(clientSessionSubscribeListener)) {
            clientSessionSubscribeListener.close();
        }
    }
}

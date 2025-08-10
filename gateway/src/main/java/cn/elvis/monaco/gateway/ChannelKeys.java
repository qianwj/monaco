package cn.elvis.monaco.gateway;

public sealed interface ChannelKeys permits ChannelKeys.NotImplemented {

    String CLIENT_SESSION_CLOSE = "client_session:close";

    String CLIENT_SESSION_SUBSCRIBE = "client_session:subscribe";

    String WILL_MESSAGE_PUBLISH_CHANNEL = "system:publish:will";

    String MESSAGE_PUBLISH_CHANNEL = "system:publish";

    final class NotImplemented implements ChannelKeys {
        private NotImplemented() {}
    }
}

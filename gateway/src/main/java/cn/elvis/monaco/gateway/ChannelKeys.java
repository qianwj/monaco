package cn.elvis.monaco.gateway;

public sealed interface ChannelKeys permits ChannelKeys.NotImplemented {

    String CLIENT_SESSION_CLOSE = "client_session:close";

    String WILL_MESSAGE_PUBLISH_CHANNEL = "system:publish:will";

    final class NotImplemented implements ChannelKeys {
        private NotImplemented() {}
    }
}

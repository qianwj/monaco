package cn.elvis.monaco.common;

public sealed interface ChannelKeys permits ChannelKeys.NotImplementedChannelKeys {

    String CONFIGURATION_CHANGE = "monaco.configuration.change";

    String SESSION_EXISTS = "monaco.session.exists";

    String SESSION_CLOSE = "monaco.session.close";

    String EXTENSION_MANAGEMENT = "monaco.extension.management";

    final class NotImplementedChannelKeys implements ChannelKeys {}
}

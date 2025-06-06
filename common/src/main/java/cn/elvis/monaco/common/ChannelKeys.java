package cn.elvis.monaco.common;

public sealed interface ChannelKeys permits ChannelKeys.NotImplementedChannelKeys {

    String CONFIGURATION_CHANGE = "monaco.configuration.change";

    String EXTENSION_AUTHENTICATE = "monaco.authenticate.extension";

    final class NotImplementedChannelKeys implements ChannelKeys {}
}

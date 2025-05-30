package cn.elvis.monaco;

public sealed interface ChannelKeys permits ChannelKeys.NotImplementedChannelKeys {

    String CONFIGURATION_CHANGE = "monaco.configuration.change";

    final class NotImplementedChannelKeys implements ChannelKeys {}
}

package cn.elvis.monaco.plugin.api.hook;

import cn.elvis.monaco.plugin.api.event.PluginEvent;
import reactor.core.publisher.Mono;

/** Best-effort listener for events emitted after Broker state commit. */
public interface PluginEventListener extends PluginHook {

    Mono<Void> onEvent(PluginEvent event);
}

package cn.elvis.monaco.plugin.runtime.registry;

import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;

/** Associates one hook with its owning plugin resources and deployment policy. */
public record HookBinding<H extends PluginHook>(PluginHandle plugin, H hook) {

    public HookBinding {
        if (plugin == null || hook == null) {
            throw new IllegalArgumentException("Hook binding components must not be null");
        }
    }
}

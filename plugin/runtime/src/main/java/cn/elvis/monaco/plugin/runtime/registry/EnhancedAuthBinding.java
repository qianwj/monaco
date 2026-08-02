package cn.elvis.monaco.plugin.runtime.registry;

import cn.elvis.monaco.plugin.api.hook.EnhancedAuthenticationProvider;
import cn.elvis.monaco.protocol.model.ConnectionId;

/** Provider affinity for one in-progress MQTT 5 AUTH exchange. */
public record EnhancedAuthBinding(
        ConnectionId connectionId,
        String authenticationMethod,
        long hookGeneration,
        HookBinding<EnhancedAuthenticationProvider> provider
) {

    public EnhancedAuthBinding {
        if (connectionId == null || authenticationMethod == null || authenticationMethod.isBlank()
                || provider == null) {
            throw new IllegalArgumentException("Enhanced authentication binding must be complete");
        }
    }
}

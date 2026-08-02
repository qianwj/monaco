package cn.elvis.monaco.plugin.runtime.registry;

import cn.elvis.monaco.protocol.model.ConnectionId;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Connection-scoped enhanced-authentication affinity store. */
public final class EnhancedAuthBindingRegistry {

    private final ConcurrentHashMap<ConnectionId, EnhancedAuthBinding> bindings = new ConcurrentHashMap<>();

    public Optional<EnhancedAuthBinding> find(ConnectionId connectionId) {
        return Optional.ofNullable(bindings.get(connectionId));
    }

    public EnhancedAuthBinding bind(EnhancedAuthBinding binding) {
        return bindings.compute(binding.connectionId(), (ignored, existing) -> {
            if (existing == null) {
                return binding;
            }
            if (existing.hookGeneration() != binding.hookGeneration()
                    || !existing.authenticationMethod().equals(binding.authenticationMethod())
                    || existing.provider() != binding.provider()) {
                throw new IllegalStateException(
                        "Connection already has a different enhanced authentication provider");
            }
            return existing;
        });
    }

    public void clear(ConnectionId connectionId) {
        bindings.remove(connectionId);
    }

    public void clearAll() {
        bindings.clear();
    }

    public int size() {
        return bindings.size();
    }
}

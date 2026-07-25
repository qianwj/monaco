package cn.elvis.monaco.protocol.property;

import java.util.List;
import java.util.Optional;

/**
 * AUTH packet properties.
 */
public record AuthProperties(
        Optional<String> authenticationMethod,
        Optional<byte[]> authenticationData,
        Optional<String> reasonString,
        List<UserProperty> userProperties
) {
    public AuthProperties {
        if (userProperties == null) {
            userProperties = List.of();
        }
    }

    public static AuthProperties empty() {
        return new AuthProperties(Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }
}

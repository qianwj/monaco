package cn.elvis.monaco.plugin.api.model;

/** One request in an MQTT 5 enhanced authentication exchange. */
public record EnhancedAuthenticationRequest(
        String authenticationMethod,
        PluginSecret authenticationData,
        boolean reauthentication
) {

    public EnhancedAuthenticationRequest {
        if (authenticationMethod == null || authenticationMethod.isBlank()) {
            throw new IllegalArgumentException("Authentication method must not be blank");
        }
        authenticationData = authenticationData == null
                ? PluginSecret.empty()
                : authenticationData;
    }
}

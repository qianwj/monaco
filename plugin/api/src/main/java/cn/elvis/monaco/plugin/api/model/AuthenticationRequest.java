package cn.elvis.monaco.plugin.api.model;

import java.util.Map;
import java.util.Optional;

/** Credentials supplied by the initial MQTT CONNECT authentication exchange. */
public record AuthenticationRequest(
        Optional<String> username,
        PluginSecret password,
        Map<String, String> attributes
) {

    public AuthenticationRequest {
        username = username == null ? Optional.empty() : username;
        password = password == null ? PluginSecret.empty() : password;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String toString() {
        return "AuthenticationRequest[username=" + username
                + ", password=" + password
                + ", attributeKeys=" + attributes.keySet() + ']';
    }
}

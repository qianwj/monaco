package cn.elvis.monaco.protocol.property;

import java.util.List;
import java.util.Optional;

/**
 * Properties for acknowledgement packets (PUBACK, PUBREC, PUBREL, PUBCOMP, UNSUBACK).
 */
public record AckProperties(
        Optional<String> reasonString,
        List<UserProperty> userProperties
) {
    public AckProperties {
        if (userProperties == null) {
            userProperties = List.of();
        }
    }

    public static AckProperties empty() {
        return new AckProperties(Optional.empty(), List.of());
    }
}

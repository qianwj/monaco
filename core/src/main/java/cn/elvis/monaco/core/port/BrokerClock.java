package cn.elvis.monaco.core.port;

import java.time.Instant;

@FunctionalInterface
public interface BrokerClock {
    Instant now();
}

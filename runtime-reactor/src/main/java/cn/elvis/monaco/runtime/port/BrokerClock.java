package cn.elvis.monaco.runtime.port;

import java.time.Instant;

@FunctionalInterface
public interface BrokerClock {
    Instant now();
}

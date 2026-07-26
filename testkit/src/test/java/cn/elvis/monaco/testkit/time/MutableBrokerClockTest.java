package cn.elvis.monaco.testkit.time;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MutableBrokerClockTest {

    @Test
    void advancesFromFixedInstant() {
        MutableBrokerClock clock = MutableBrokerClock.startingAt(Instant.parse("2026-07-25T00:00:00Z"));

        clock.advance(Duration.ofSeconds(5));

        assertEquals(Instant.parse("2026-07-25T00:00:05Z"), clock.now());
    }

    @Test
    void rejectsNegativeDurationAndOverflow() {
        MutableBrokerClock clock = MutableBrokerClock.startingAt(Instant.MAX.minusSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> clock.advance(Duration.ofNanos(-1)));
        assertThrows(DateTimeException.class, () -> clock.advance(Duration.ofSeconds(2)));
    }
}

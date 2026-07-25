package cn.elvis.monaco.protocol.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QoSTest {

    @Test
    void valueOfValidValues() {
        assertEquals(QoS.AT_MOST_ONCE, QoS.valueOf(0));
        assertEquals(QoS.AT_LEAST_ONCE, QoS.valueOf(1));
        assertEquals(QoS.EXACTLY_ONCE, QoS.valueOf(2));
    }

    @Test
    void valueOfInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> QoS.valueOf(-1));
        assertThrows(IllegalArgumentException.class, () -> QoS.valueOf(3));
    }

    @Test
    void value() {
        assertEquals(0, QoS.AT_MOST_ONCE.value());
        assertEquals(1, QoS.AT_LEAST_ONCE.value());
        assertEquals(2, QoS.EXACTLY_ONCE.value());
    }

    @Test
    void minReturnsLowerQoS() {
        assertEquals(QoS.AT_MOST_ONCE, QoS.EXACTLY_ONCE.min(QoS.AT_MOST_ONCE));
        assertEquals(QoS.AT_LEAST_ONCE, QoS.EXACTLY_ONCE.min(QoS.AT_LEAST_ONCE));
        assertEquals(QoS.AT_MOST_ONCE, QoS.AT_MOST_ONCE.min(QoS.EXACTLY_ONCE));
        assertEquals(QoS.EXACTLY_ONCE, QoS.EXACTLY_ONCE.min(QoS.EXACTLY_ONCE));
    }
}

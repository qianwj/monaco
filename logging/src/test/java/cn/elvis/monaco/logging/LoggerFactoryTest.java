package cn.elvis.monaco.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoggerFactoryTest {

    @Test
    void getLoggerByName() {
        Logger logger = LoggerFactory.getLogger("test.factory");
        assertNotNull(logger);
        assertEquals("test.factory", logger.getName());
    }

    @Test
    void getLoggerByClass() {
        Logger logger = LoggerFactory.getLogger(LoggerFactoryTest.class);
        assertNotNull(logger);
        assertEquals(LoggerFactoryTest.class.getName(), logger.getName());
    }

    @Test
    void sameNameReturnsSameInstance() {
        Logger a = LoggerFactory.getLogger("test.cache");
        Logger b = LoggerFactory.getLogger("test.cache");
        assertSame(a, b);
    }
}

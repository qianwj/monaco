package cn.elvis.monaco.testkit.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SequenceIdGeneratorTest {

    @Test
    void generatesPredictableIds() {
        SequenceIdGenerator generator = new SequenceIdGenerator("message-", 7);

        assertEquals("message-7", generator.nextId());
        assertEquals("message-8", generator.nextId());
    }

    @Test
    void rejectsNegativeStartAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new SequenceIdGenerator("id-", -1));

        SequenceIdGenerator generator = new SequenceIdGenerator("id-", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, generator::nextId);
    }
}

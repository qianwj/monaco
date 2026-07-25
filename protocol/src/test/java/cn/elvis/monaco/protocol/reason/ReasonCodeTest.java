package cn.elvis.monaco.protocol.reason;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReasonCodeTest {

    @Test
    void successCodesAreNotErrors() {
        assertTrue(ReasonCode.SUCCESS.isSuccess());
        assertFalse(ReasonCode.SUCCESS.isError());
        assertTrue(ReasonCode.GRANTED_QOS_1.isSuccess());
        assertTrue(ReasonCode.NO_MATCHING_SUBSCRIBERS.isSuccess());
    }

    @Test
    void errorCodesAreErrors() {
        assertTrue(ReasonCode.UNSPECIFIED_ERROR.isError());
        assertFalse(ReasonCode.UNSPECIFIED_ERROR.isSuccess());
        assertTrue(ReasonCode.MALFORMED_PACKET.isError());
        assertTrue(ReasonCode.PROTOCOL_ERROR.isError());
        assertTrue(ReasonCode.NOT_AUTHORIZED.isError());
    }

    @Test
    void codeValues() {
        assertEquals(0x00, ReasonCode.SUCCESS.code());
        assertEquals(0x04, ReasonCode.DISCONNECT_WITH_WILL.code());
        assertEquals(0x80, ReasonCode.UNSPECIFIED_ERROR.code());
        assertEquals(0x81, ReasonCode.MALFORMED_PACKET.code());
        assertEquals(0x8E, ReasonCode.SESSION_TAKEN_OVER.code());
        assertEquals(0x97, ReasonCode.QUOTA_EXCEEDED.code());
    }
}

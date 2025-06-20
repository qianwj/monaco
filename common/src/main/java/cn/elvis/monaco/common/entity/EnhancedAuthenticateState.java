package cn.elvis.monaco.common.entity;

public enum EnhancedAuthenticateState {
    SUCCESS((byte) 0x00),
    FAILURE((byte) 0x01),
    CONTINUE((byte) 0x18),
    RE_AUTHENTICATE((byte) 0x19),
    ;

    private final byte code;
    EnhancedAuthenticateState(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }
}
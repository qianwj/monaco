package cn.elvis.monaco.gateway.exception;

/**
 * Protocol exception
 */
public abstract class ProtocolException extends Exception {

    private final int code;

    private final String message;

    protected ProtocolException(int code, String message) {
        this.code = code;
        this.message = String.format("[%d] %s", code, message);
    }

    public int code() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }


}

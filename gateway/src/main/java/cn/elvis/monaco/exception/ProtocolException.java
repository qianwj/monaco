package cn.elvis.monaco.exception;

/**
 * Protocol exception
 */
public abstract class ProtocolException extends Exception {

    private final int code;

    private final int subCode;

    private final String message;

    protected ProtocolException(int code, int subCode, String message) {
        this.code = code;
        this.subCode = subCode;
        if (subCode > 0) {
            this.message = String.format("[%d] %s", subCode, message);
        } else {
            this.message = message;
        }
    }

    public int code() {
        return code;
    }

    public int subCode() {
        return subCode;
    }

    @Override
    public String getMessage() {
        return message;
    }


}

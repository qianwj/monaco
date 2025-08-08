package cn.elvis.monaco.gateway.exception;

public final class QuotaExceedException extends ProtocolException {

    QuotaExceedException(int code, String message) {
        super(code, message);
    }


}

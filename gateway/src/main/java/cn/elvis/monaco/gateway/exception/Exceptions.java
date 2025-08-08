package cn.elvis.monaco.gateway.exception;

public final class Exceptions {

    public static ProtocolException receiveMaximumExceeded() {
        return new QuotaExceedException(ExceptionCode.RECEIVE_MAXIMUM_EXCEEDED, "receive maximum exceeded.");
    }
}

package cn.elvis.monaco.gateway.exception;

public class NoMatchingSubscribersException extends ProtocolException {

    NoMatchingSubscribersException(int code, String message) {
        super(code, message);
    }
}

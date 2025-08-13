package cn.elvis.monaco.gateway.exception;

public final class TopicNameInvalidException extends ProtocolException {
    TopicNameInvalidException(int code, String message) {
        super(code, message);
    }
}

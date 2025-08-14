package cn.elvis.monaco.exception;

public final class TopicNameInvalidException extends ProtocolException {
    TopicNameInvalidException(String message) {
        super(ExceptionCode.TOPIC_NAME_INVALID, 0, message);
    }
}

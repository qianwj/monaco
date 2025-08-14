package cn.elvis.monaco.exception;

public class NoMatchingSubscribersException extends ProtocolException {

    NoMatchingSubscribersException(String message) {
        super(ExceptionCode.NO_MATCHING_SUBSCRIBERS, 0, message);
    }
}

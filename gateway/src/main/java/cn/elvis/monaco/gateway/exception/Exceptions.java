package cn.elvis.monaco.gateway.exception;

public final class Exceptions {

    public static ProtocolException receiveMaximumExceeded() {
        return new QuotaExceedException(ExceptionCode.RECEIVE_MAXIMUM_EXCEEDED, "receive maximum exceeded.");
    }

    public static ProtocolException topicNameInvalid(String topicName) {
        return new TopicNameInvalidException(ExceptionCode.TOPIC_NAME_INVALID, "invalid topic: " + topicName);
    }

    public static ProtocolException noMatchingSubscribers(String topicName) {
        return new NoMatchingSubscribersException(ExceptionCode.NO_MATCHING_SUBSCRIBERS, "no matching subscribers: " + topicName);
    }
}

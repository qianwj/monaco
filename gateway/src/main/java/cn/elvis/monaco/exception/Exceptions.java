package cn.elvis.monaco.exception;

public final class Exceptions {

    private static final QuotaExceedException publishQueueFulfilled = new QuotaExceedException(ExceptionCode.QUOTA_EXCEEDED, "publish queue fulfilled");

    public static ProtocolException receiveMaximumExceeded() {
        return new QuotaExceedException(ExceptionCode.RECEIVE_MAXIMUM_EXCEEDED, "receive maximum exceeded.");
    }

    public static ProtocolException publishQueueFulfilled() {
        return publishQueueFulfilled;
    }


    public static ProtocolException invalidTopicAlias(int topicAlias) {
        return new ImplementationSpecificException(ExceptionCode.TOPIC_ALIAS_INVALID, "invalid topic alias: " + topicAlias);
    }

    public static ProtocolException topicNameInvalid(String topicName) {
        return new TopicNameInvalidException("invalid topic: " + topicName);
    }

    public static ProtocolException noMatchingSubscribers(String topicName) {
        return new NoMatchingSubscribersException("no matching subscribers: " + topicName);
    }
}

package cn.elvis.monaco.exception;

/**
 * Exception code list
 *
 * @author qianwj
 * @since  0.0.1
 */
sealed interface ExceptionCode permits ExceptionCode.NotImplementedExceptionCode {

    int NO_MATCHING_SUBSCRIBERS = 0x10;
    int IMPLEMENTATION_SPECIFIC_ERROR = 0x83;
    int TOPIC_NAME_INVALID = 0x90;
    int QUOTA_EXCEEDED = 0x97;

    int RECEIVE_MAXIMUM_EXCEEDED = 20001;
    int TOPIC_ALIAS_INVALID = 20002;

    final class NotImplementedExceptionCode implements ExceptionCode {
        private NotImplementedExceptionCode() {}
    }
}

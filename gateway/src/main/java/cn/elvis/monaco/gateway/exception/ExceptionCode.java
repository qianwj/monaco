package cn.elvis.monaco.gateway.exception;

/**
 * Exception code list
 *
 * @author qianwj
 * @since  0.0.1
 */
sealed interface ExceptionCode permits ExceptionCode.NotImplementedExceptionCode {

    int NO_MATCHING_SUBSCRIBERS = 0x10;
    int TOPIC_NAME_INVALID = 0x90;
    int QUOTA_EXCEEDED = 0x97;

    int RECEIVE_MAXIMUM_EXCEEDED = 20001;

    final class NotImplementedExceptionCode implements ExceptionCode {
        private NotImplementedExceptionCode() {}
    }
}

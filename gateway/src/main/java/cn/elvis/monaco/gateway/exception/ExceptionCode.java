package cn.elvis.monaco.gateway.exception;

/**
 * Exception code list
 *
 * @author qianwj
 * @since  0.0.1
 */
sealed interface ExceptionCode permits ExceptionCode.NotImplementedExceptionCode {

    int RECEIVE_MAXIMUM_EXCEEDED = 20001;

    final class NotImplementedExceptionCode implements ExceptionCode {
        private NotImplementedExceptionCode() {}
    }
}

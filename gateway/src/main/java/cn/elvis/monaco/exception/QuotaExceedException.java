package cn.elvis.monaco.exception;

public final class QuotaExceedException extends ProtocolException {

    QuotaExceedException(int subCode, String message) {
        super(ExceptionCode.QUOTA_EXCEEDED, subCode, message);
    }

}

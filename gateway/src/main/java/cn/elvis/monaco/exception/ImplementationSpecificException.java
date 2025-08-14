package cn.elvis.monaco.exception;

public class ImplementationSpecificException extends ProtocolException {

    ImplementationSpecificException(int subCode, String message) {
        super(ExceptionCode.IMPLEMENTATION_SPECIFIC_ERROR, subCode, message);
    }
}

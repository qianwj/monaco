package cn.elvis.monaco.session;

public interface ClientIdValidator {

    boolean isValid(String clientId);

    static ClientIdValidator defaultValidator(int maxLength) {
        return new DefaultClientIdValidator(maxLength);
    }

    class DefaultClientIdValidator implements ClientIdValidator {

        private final int maxLength;

        public DefaultClientIdValidator(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public boolean isValid(String clientId) {
            return clientId != null && maxLength > 0 && clientId.length() <= maxLength;
        }
    }
}

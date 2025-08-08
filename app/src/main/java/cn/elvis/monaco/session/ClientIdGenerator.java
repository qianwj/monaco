package cn.elvis.monaco.session;

import cn.elvis.monaco.utils.ULID;

public interface ClientIdGenerator {

    String generate();

    static ClientIdGenerator defaultGenerator() {
        return new DefaultClientIdGenerator();
    }

    class DefaultClientIdGenerator implements ClientIdGenerator {

        @Override
        public String generate() {
            return ULID.random();
        }
    }
}

package cn.elvis.monaco.persistence;

import cn.elvis.monaco.common.entity.Session;
import io.vertx.sqlclient.Pool;

public class SessionRepository {

    private final Pool pool;

    public SessionRepository(Pool pool) {
        this.pool = pool;
    }

    public void createSession(Session session) {
        pool.getConnection().compose(conn -> {
           return conn.preparedQuery("insert into session(id, expiredTime, )").execute();
        });
    }
}

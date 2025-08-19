package cn.elvis.monaco.store;

import cn.elvis.monaco.session.ClientSession;

import java.util.List;
import java.util.Optional;

public interface ClientSessionStore {

    void add(ClientSession clientSession);

    Optional<ClientSession> get(String clientId);

    Optional<ClientSession> remove(String clientId);

    List<ClientSession> expired();

    int total();
}

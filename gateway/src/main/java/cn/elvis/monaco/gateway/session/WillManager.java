package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.entity.WillMessage;

public interface WillManager {

    void addWill(String clientId, WillMessage willMessage);

    void stop();
}

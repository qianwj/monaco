package cn.elvis.monaco.gateway.manager;

import cn.elvis.monaco.gateway.entity.WillMessage;

public interface WillManager extends Manager {

    void addWill(String clientId, WillMessage willMessage);
}

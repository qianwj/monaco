package cn.elvis.monaco.manager;

import cn.elvis.monaco.entity.WillMessage;

public interface WillManager extends Manager {

    void addWill(String clientId, WillMessage willMessage);
}

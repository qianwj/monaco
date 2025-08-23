package cn.elvis.monaco.store;

import cn.elvis.monaco.entity.PublishMessage;

import java.util.Optional;
import java.util.Set;

public interface MessageStore {

    void push(String clientId, PublishMessage publishMessage);

    Optional<PublishMessage> poll(String clientId);

    Set<String> getReceivers(int packetId);

}

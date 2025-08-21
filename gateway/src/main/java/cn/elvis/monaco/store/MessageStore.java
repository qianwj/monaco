package cn.elvis.monaco.store;

import cn.elvis.monaco.entity.PublishMessage;

import java.util.Optional;

public interface MessageStore {

    void push(String clientId, PublishMessage publishMessage);

    Optional<PublishMessage> poll(String clientId);
}

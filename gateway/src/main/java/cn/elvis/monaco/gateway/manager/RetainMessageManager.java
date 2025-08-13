package cn.elvis.monaco.gateway.manager;

import cn.elvis.monaco.gateway.entity.PublishMessage;

/**
 * Managing retained messages
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface RetainMessageManager extends Manager {

    void addMessage(PublishMessage message);
}

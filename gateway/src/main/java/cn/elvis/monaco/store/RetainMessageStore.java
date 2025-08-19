package cn.elvis.monaco.store;

import cn.elvis.monaco.entity.PublishMessage;

/**
 * Store retain message
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface RetainMessageStore {

    void setRetain(PublishMessage message);

    void removeRetain(String topic);
}

package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

import java.util.*;

/**
 * MQTT主题订阅树实现，支持通配符匹配
 */
//public class TopicTree {
//    // 根节点
//    private final TreeNode root;
//
//    public TopicTree() {
//        root = new TreeNode();
//    }
//
//    /**
//     * 添加订阅
//     * @param subscription 订阅对象，包含客户端ID、主题和QoS
//     */
//    public void addSubscription(Subscription subscription) {
//        if (subscription == null) {
//            throw new IllegalArgumentException("Subscription cannot be null");
//        }
//
//        String topic = subscription.topic().filter();
//        if (topic == null || topic.isEmpty()) {
//            throw new IllegalArgumentException("Topic cannot be null or empty");
//        }
//
//        String[] levels = splitTopic(topic);
//        TreeNode current = root;
//
//        for (int i = 0; i < levels.length; i++) {
//            String level = levels[i];
//
//            // 检查是否为多级通配符，只能在最后一级
//            if (level.equals("#")) {
//                if (i != levels.length - 1) {
//                    throw new IllegalArgumentException("Multi-level wildcard # can only be the last level");
//                }
//                current.addMultiLevelSubscription(subscription);
//                break;
//            }
//
//            // 处理单级通配符或普通级别
//            if (level.equals("+")) {
//                if (current.getPlusChild() == null) {
//                    current.setPlusChild(new TreeNode());
//                }
//                current = current.getPlusChild();
//            } else {
//                if (!current.getChildren().containsKey(level)) {
//                    current.getChildren().put(level, new TreeNode());
//                }
//                current = current.getChildren().get(level);
//            }
//
//            // 如果是最后一级，添加订阅
//            if (i == levels.length - 1) {
//                current.addExactSubscription(subscription);
//            }
//        }
//    }
//
//    public void removeSubscription(Topic topic, String clientId) {
//        if (topic == null) {
//            return;
//        }
//
//        String[] levels = splitTopic(topic.filter());
//        TreeNode current = root;
//
//        for (int i = 0; i < levels.length; i++) {
//            String level = levels[i];
//
//            if (level.equals("#")) {
//                if (i == levels.length - 1) {
//                    current.removeMultiLevelSubscription(topic, clientId);
//                }
//                break;
//            }
//
//            if (level.equals("+")) {
//                if (current.getPlusChild() == null) {
//                    break;
//                }
//                current = current.getPlusChild();
//            } else {
//                if (!current.getChildren().containsKey(level)) {
//                    break;
//                }
//                current = current.getChildren().get(level);
//            }
//
//            if (i == levels.length - 1) {
//                current.removeExactSubscription(topic, clientId);
//            }
//        }
//    }
//
//    /**
//     * 查找与指定主题匹配的所有订阅
//     * @param topic 发布的主题（不能包含通配符）
//     * @return 匹配的订阅集合
//     */
//    public Set<Subscription> findMatchingSubscriptions(String topic) {
//        String[] levels = splitTopic(topic.filter());
//        Set<Subscription> result = new HashSet<>();
//
//        // 递归查找所有匹配的订阅
//        findMatchingSubscriptions(root, levels, 0, result);
//
//        return result;
//    }
//
//    /**
//     * 递归查找匹配的订阅
//     */
//    private void findMatchingSubscriptions(TreeNode node, String[] levels, int levelIndex, Set<Subscription> result) {
//        // 如果已经处理完所有级别
//        if (levelIndex == levels.length) {
//            // 添加所有精确匹配的订阅
//            result.addAll(node.getExactSubscriptions());
//            return;
//        }
//
//        String currentLevel = levels[levelIndex];
//
//        // 1. 添加所有多级通配符订阅
//        result.addAll(node.getMultiLevelSubscriptions());
//
//        // 2. 处理单级通配符匹配
//        if (node.getPlusChild() != null) {
//            findMatchingSubscriptions(node.getPlusChild(), levels, levelIndex + 1, result);
//        }
//
//        // 3. 处理精确匹配
//        if (node.getChildren().containsKey(currentLevel)) {
//            findMatchingSubscriptions(node.getChildren().get(currentLevel), levels, levelIndex + 1, result);
//        }
//    }
//
//    /**
//     * 将主题拆分为级别数组
//     */
//    private String[] splitTopic(String topic) {
//        // 处理以斜杠开头的主题
//        if (topic.startsWith("/")) {
//            topic = topic.substring(1);
//        }
//        // 处理以斜杠结尾的主题（非通配符情况）
//        if (topic.endsWith("/") && !topic.endsWith("#/") && !topic.equals("/")) {
//            topic = topic.substring(0, topic.length() - 1);
//        }
//        return topic.split("/");
//    }
//
//    /**
//     * 树节点类，表示主题的一个级别
//     */
//    private static class TreeNode {
//        // 存储当前级别的精确匹配订阅
//        private final Set<Subscription> exactSubscriptions;
//        // 存储当前级别的多级通配符(#)订阅
//        private final Set<Subscription> multiLevelSubscriptions;
//        // 子节点，键是级别名称，值是对应的节点
//        private final Map<String, TreeNode> children;
//        // 单级通配符(+)对应的子节点
//        private TreeNode plusChild;
//
//        public TreeNode() {
//            exactSubscriptions = new HashSet<>();
//            multiLevelSubscriptions = new HashSet<>();
//            children = new HashMap<>();
//            plusChild = null;
//        }
//
//        public Set<Subscription> getExactSubscriptions() {
//            return new HashSet<>(exactSubscriptions);
//        }
//
//        public void addExactSubscription(Subscription subscription) {
//            exactSubscriptions.add(subscription);
//        }
//
//        public void removeExactSubscription(Subscription subscription) {
//            exactSubscriptions.remove(subscription);
//        }
//
//        public Set<Subscription> getMultiLevelSubscriptions() {
//            return new HashSet<>(multiLevelSubscriptions);
//        }
//
//        public void addMultiLevelSubscription(Subscription subscription) {
//            multiLevelSubscriptions.add(subscription);
//        }
//
//        public void removeMultiLevelSubscription(Subscription subscription) {
//            multiLevelSubscriptions.remove(subscription);
//        }
//
//        public Map<String, TreeNode> getChildren() {
//            return children;
//        }
//
//        public TreeNode getPlusChild() {
//            return plusChild;
//        }
//
//        public void setPlusChild(TreeNode plusChild) {
//            this.plusChild = plusChild;
//        }
//    }
//
//    // 测试方法
//    public static void main(String[] args) {
//        MQTTTopicTree topicTree = new MQTTTopicTree();
//
//        // 添加一些订阅
//        topicTree.addSubscription(new Subscription("client1", "sensor/temp/room1", 0));
//        topicTree.addSubscription(new Subscription("client2", "sensor/+/room1", 1));
//        topicTree.addSubscription(new Subscription("client3", "sensor/temp/#", 2));
//        topicTree.addSubscription(new Subscription("client4", "#", 0));
//        topicTree.addSubscription(new Subscription("client5", "sensor/#", 1));
//
//        // 测试匹配
//        System.out.println("匹配 sensor/temp/room1 的订阅: " +
//                topicTree.findMatchingSubscriptions("sensor/temp/room1"));
//
//        System.out.println("匹配 sensor/humidity/room1 的订阅: " +
//                topicTree.findMatchingSubscriptions("sensor/humidity/room1"));
//
//        System.out.println("匹配 sensor/temp/room2 的订阅: " +
//                topicTree.findMatchingSubscriptions("sensor/temp/room2"));
//
//        // 移除一个订阅
//        topicTree.removeSubscription(new Subscription("client4", "#", 0));
//        System.out.println("移除 client4 对 # 的订阅后，匹配 sensor/temp/room1 的订阅: " +
//                topicTree.findMatchingSubscriptions("sensor/temp/room1"));
//    }
//}

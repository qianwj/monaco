package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

import java.util.*;

/**
 * Mqtt Topic Tree Node
 * @author qianwj
 * @since  0.0.1
 */
public final class TopicTreeNode {

    private final Map<String, TopicTreeNode> children = new HashMap<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final String segment;
    private final boolean shareable;
    
    // 特殊节点存储
    private TopicTreeNode singleWildcardNode; // 存储 + 通配符节点
    private TopicTreeNode multiWildcardNode;  // 存储 # 通配符节点

    public TopicTreeNode(String segment, boolean shareable) {
        this.segment = segment;
        this.shareable = shareable;
    }

    List<TopicTreeNode> children() {
        List<TopicTreeNode> result = new ArrayList<>(children.values());
        if (singleWildcardNode != null) {
            result.add(singleWildcardNode);
        }
        if (multiWildcardNode != null) {
            result.add(multiWildcardNode);
        }
        return result;
    }

    String segment() {
        return segment;
    }

    void addChild(String[] segments, int level, Subscription subscription) {
        // 如果已经处理完所有层级，则在当前节点添加订阅
        if (level >= segments.length) {
            subscriptions.add(subscription);
            return;
        }

        String currentSegment = segments[level];
        
        // 处理单级通配符 (+)
        if (Topics.SINGLE_WILDCARD_TOKEN.equals(currentSegment)) {
            if (singleWildcardNode == null) {
                singleWildcardNode = new TopicTreeNode(Topics.SINGLE_WILDCARD_TOKEN, this.shareable);
            }
            
            if (level == segments.length - 1) {
                singleWildcardNode.subscriptions.add(subscription);
            } else {
                singleWildcardNode.addChild(segments, level + 1, subscription);
            }
            return;
        }
        
        // 处理多级通配符 (#)
        if (Topics.MULTI_WILDCARD_TOKEN.equals(currentSegment)) {
            if (multiWildcardNode == null) {
                multiWildcardNode = new TopicTreeNode(Topics.MULTI_WILDCARD_TOKEN, this.shareable);
            }
            multiWildcardNode.subscriptions.add(subscription);
            return;
        }
        
        // 如果是最后一级，在当前节点添加订阅
        if (level == segments.length - 1) {
            // 获取或创建子节点
            TopicTreeNode child = children.computeIfAbsent(currentSegment, 
                    k -> new TopicTreeNode(currentSegment, this.shareable));
            child.subscriptions.add(subscription);
            return;
        }
        
        // 处理中间层级
        TopicTreeNode child = children.computeIfAbsent(currentSegment, 
                k -> new TopicTreeNode(currentSegment, this.shareable));
        child.addChild(segments, level + 1, subscription);
    }

    void removeSubscriptions(String[] segments, int level, String clientIdentifier) {
        String currentSegment = segments[level];
        switch (currentSegment) {
            case Topics.SINGLE_WILDCARD_TOKEN ->
                Optional.ofNullable(singleWildcardNode)
                        .ifPresent(n -> n.removeSubscriptions(segments, level + 1, clientIdentifier));
            case Topics.MULTI_WILDCARD_TOKEN ->
                Optional.ofNullable(multiWildcardNode)
                        .ifPresent(n -> n.removeSubscriptions(segments, level + 1, clientIdentifier));
            default -> {
                if (Objects.equals(segment, Topics.SEGMENT_SEPARATOR)) {
                    for (TopicTreeNode child : children()) {
                        child.removeSubscriptions(segments, level, clientIdentifier);
                    }
                } else if (Objects.equals(currentSegment, segment)) {
                    if (level == segments.length - 1) {
                        subscriptions.removeIf(subscription -> Objects.equals(subscription.clientIdentifier(), clientIdentifier));
                    }
                    for (TopicTreeNode child : children()) {
                        child.removeSubscriptions(segments, level + 1, clientIdentifier);
                    }
                }
            }
        }
    }

    List<Subscription> subscriptions(String[] segments, int level) {
        List<Subscription> result = new ArrayList<>();
        
        // 如果已经处理完所有层级，返回当前节点的订阅
        if (level >= segments.length) {
            result.addAll(subscriptions);
            return result;
        }
        
        // 获取当前层级
        String currentSegment = segments[level];
        
        // 处理查询主题中的通配符
        if (Topics.SINGLE_WILDCARD_TOKEN.equals(currentSegment)) {
            // 如果查询主题中包含单级通配符，需要匹配所有可能的子节点
            // 添加所有子节点的匹配结果
            for (TopicTreeNode child : children.values()) {
                result.addAll(child.subscriptions(segments, level + 1));
            }
            
            // 同时也要匹配单级通配符节点
            if (singleWildcardNode != null) {
                result.addAll(singleWildcardNode.subscriptions(segments, level + 1));
            }
            
            // 多级通配符匹配剩余所有层级
            if (multiWildcardNode != null) {
                result.addAll(multiWildcardNode.subscriptions);
            }
            
            return result;
        }
        
        if (Topics.MULTI_WILDCARD_TOKEN.equals(currentSegment)) {
            // 处理查询主题中的多级通配符
            // 检查是否是最后一个层级
            if (level == segments.length - 1) {
                // 如果是最后一个层级，则匹配所有子树
                collectAllSubscriptions(result);
                return result;
            }
            
            // 如果不是最后一个层级，则需要处理特殊情况
            // 例如 a/#/c 应该匹配 a/b/c 和 a/b/b/c
            
            // 1. 添加当前节点的订阅
            result.addAll(subscriptions);
            
            // 2. 添加多级通配符节点的订阅（如果有）
            if (multiWildcardNode != null) {
                result.addAll(multiWildcardNode.subscriptions);
            }
            
            // 3. 处理后续层级的匹配
            // 对于 a/#/c 这样的模式，我们需要匹配:
            // - a/b/c (一个中间层级)
            // - a/b/b/c (多个中间层级)
            
            // 获取最后一个层级
            String lastSegment = segments[segments.length - 1];
            
            // 递归查找所有可能的路径
            findMatchingPaths(result, lastSegment);
            
            return result;
        }
        
        // 如果是最后一级，检查是否有匹配的子节点
        if (level == segments.length - 1) {
            // 添加当前节点的订阅（如果有）
            result.addAll(subscriptions);
            
            // 检查精确匹配
            TopicTreeNode exactChild = children.get(currentSegment);
            if (exactChild != null) {
                result.addAll(exactChild.subscriptions);
            }
            
            // 检查单级通配符匹配
            if (singleWildcardNode != null) {
                result.addAll(singleWildcardNode.subscriptions);
            }
            
            // 检查多级通配符匹配
            if (multiWildcardNode != null) {
                result.addAll(multiWildcardNode.subscriptions);
            }
            
            return result;
        }
        
        // 处理中间层级
        // 添加当前节点的订阅（如果有）
        result.addAll(subscriptions);
        
        // 精确匹配
        TopicTreeNode exactChild = children.get(currentSegment);
        if (exactChild != null) {
            result.addAll(exactChild.subscriptions(segments, level + 1));
        }
        
        // 单级通配符匹配
        if (singleWildcardNode != null) {
            result.addAll(singleWildcardNode.subscriptions(segments, level + 1));
        }
        
        // 多级通配符匹配
        if (multiWildcardNode != null) {
            result.addAll(multiWildcardNode.subscriptions);
        }
        
        return result;
    }
    
    // 收集所有子树中的订阅
    private void collectAllSubscriptions(List<Subscription> result) {
        // 添加当前节点的订阅
        result.addAll(subscriptions);
        
        // 添加所有子节点的订阅
        for (TopicTreeNode child : children.values()) {
            child.collectAllSubscriptions(result);
        }
        
        // 添加通配符节点的订阅
        if (singleWildcardNode != null) {
            singleWildcardNode.collectAllSubscriptions(result);
        }
        
        if (multiWildcardNode != null) {
            multiWildcardNode.collectAllSubscriptions(result);
        }
    }
    
    // 查找所有匹配指定末尾段的路径
    private void findMatchingPaths(List<Subscription> result, String lastSegment) {
        // 检查当前节点的子节点是否匹配最后一个段
        TopicTreeNode lastSegmentNode = children.get(lastSegment);
        if (lastSegmentNode != null) {
            result.addAll(lastSegmentNode.subscriptions);
        }
        
        // 检查单级通配符节点
        if (singleWildcardNode != null) {
            result.addAll(singleWildcardNode.subscriptions);
        }
        
        // 递归检查所有子节点
        for (TopicTreeNode child : children.values()) {
            child.findMatchingPaths(result, lastSegment);
        }
        
        // 递归检查单级通配符节点
        if (singleWildcardNode != null) {
            singleWildcardNode.findMatchingPaths(result, lastSegment);
        }
    }
}

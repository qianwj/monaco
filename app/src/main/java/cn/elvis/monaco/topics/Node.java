package cn.elvis.monaco.topics;

import java.util.ArrayList;
import java.util.List;

public record Node(char token, List<Node> children, List<Subscription> subscriptions) {

    public Node(char token) {
        this(token, new ArrayList<>(), new ArrayList<>());
    }
}

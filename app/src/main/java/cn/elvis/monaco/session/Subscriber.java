package cn.elvis.monaco.session;

import cn.elvis.monaco.configuration.SessionConfiguration;
import cn.elvis.monaco.topics.Subscription;
import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import cn.elvis.monaco.utils.MqttPropertiesReader;
import io.vertx.core.Handler;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

final class Subscriber {

    private static final Logger log = LogManager.getLogger(Subscriber.class);

    private final String sessionId;

    private final MqttEndpoint endpoint;

    private final OperateChecker checker;

    private final boolean subscriptionIdentifierAvailable;

    private final boolean sharedSubscriptionAvailable;

    private final boolean wildcardSubscriptionAvailable;

    private final boolean withProblemInfo;

    Subscriber(String sessionId,
               MqttEndpoint endpoint,
               OperateChecker checker,
               SessionConfiguration sessionConfiguration,
               boolean withProblemInfo) {
        this.sessionId = sessionId;
        this.endpoint = endpoint;
        this.checker = checker;
        this.subscriptionIdentifierAvailable = sessionConfiguration.isSubscriptionIdentifierAvailable();
        this.sharedSubscriptionAvailable = sessionConfiguration.isShardSubscriptionAvailable();
        this.wildcardSubscriptionAvailable = sessionConfiguration.isWildcardSubscriptionAvailable();
        this.withProblemInfo = withProblemInfo;
        endpoint.subscribeHandler(handleSubscribe());
    }

    private Handler<MqttSubscribeMessage> handleSubscribe() {
        return mqttSubscribeMessage -> {
            if (checker.allowed()) {
                var properties = new MqttPropertiesReader(mqttSubscribeMessage.properties());
                int packetId = mqttSubscribeMessage.messageId();
                int subscriptionId = properties.subscriptionId();
                List<MqttSubAckReasonCode> reasonCodes = new ArrayList<>();
                List<Subscription> subscriptions = new ArrayList<>();
                var ackProperties = MqttPropertiesBuilder.create();
                try {
                    if (subscriptionIdentifierAvailable) {
                        // todo: find subscriptions from persistence
                    } else {
                        boolean hasError = false;
                        var reasons = new ArrayList<String>();
                        for (MqttTopicSubscription topicSubscription : mqttSubscribeMessage.topicSubscriptions()) {
                            var subscription = Subscription.from(sessionId, subscriptionId, topicSubscription);
                            if (subscription.invalidTopicFilter()) {
                                hasError = true;
                                reasonCodes.add(MqttSubAckReasonCode.TOPIC_FILTER_INVALID);
                                if (withProblemInfo) {
                                    reasons.add("topic filter  `" + subscription.topicFilter() + "` invalid");
                                }
                            } else if (!sharedSubscriptionAvailable && subscription.isShared()) {
                                hasError = true;
                                reasonCodes.add(MqttSubAckReasonCode.SHARED_SUBSCRIPTIONS_NOT_SUPPORTED);
                                if (withProblemInfo) {
                                    reasons.add("shared subscriptions not supported");
                                }
                            } else if (!wildcardSubscriptionAvailable && subscription.isWildcard()) {
                                hasError = true;
                                reasonCodes.add(MqttSubAckReasonCode.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED);
                                if (withProblemInfo) {
                                    reasons.add("wildcard subscriptions not supported");
                                }
                            } else {
                                subscriptions.add(subscription);
                                if (withProblemInfo) {
                                    reasons.add("-");
                                }
                                reasonCodes.add(MqttSubAckReasonCode.qosGranted(subscription.qos()));
                            }
                        }
                        if (hasError) {
                            if (withProblemInfo) {
                                ackProperties.withReasonString(String.join(";", reasons));
                            }
                            endpoint.subscribeAcknowledge(packetId, reasonCodes, ackProperties.build());
                        }
                    }
                    var grantedQoS = subscriptions.stream().map(Subscription::qos).toList();
                    endpoint.subscribeAcknowledge(packetId, grantedQoS);
                } catch (Exception e) {
                    log.error("Subscribe failed", e);
                    if (withProblemInfo) {
                        // todo: format reason string
                        ackProperties.withReasonString(e.getMessage());
                    }
                    reasonCodes = subscriptions.stream().map(s -> MqttSubAckReasonCode.UNSPECIFIED_ERROR).toList();
                    endpoint.subscribeAcknowledge(packetId, reasonCodes, ackProperties.build());
                }
            }
        };
    }


}

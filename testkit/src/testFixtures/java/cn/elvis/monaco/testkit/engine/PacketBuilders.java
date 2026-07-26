package cn.elvis.monaco.testkit.engine;

import cn.elvis.monaco.protocol.model.Payload;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.protocol.packet.Subscription;
import cn.elvis.monaco.protocol.packet.WillMessage;
import cn.elvis.monaco.protocol.property.AckProperties;
import cn.elvis.monaco.protocol.property.ConnectProperties;
import cn.elvis.monaco.protocol.property.DisconnectProperties;
import cn.elvis.monaco.protocol.property.PublishProperties;
import cn.elvis.monaco.protocol.property.SubscribeProperties;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.Arrays;
import java.util.List;

public final class PacketBuilders {

    private static final String DEFAULT_CLIENT_ID = "test-client";
    private static final String DEFAULT_TOPIC_NAME = "test/topic";
    private static final String DEFAULT_TOPIC_FILTER = "test/#";

    private PacketBuilders() {
    }

    public static ConnectBuilder connect() {
        return new ConnectBuilder();
    }

    public static PublishBuilder publish() {
        return new PublishBuilder();
    }

    public static SubscribeBuilder subscribe() {
        return new SubscribeBuilder();
    }

    public static UnsubscribeBuilder unsubscribe() {
        return new UnsubscribeBuilder();
    }

    public static PubAckBuilder pubAck() {
        return new PubAckBuilder();
    }

    public static PubRecBuilder pubRec() {
        return new PubRecBuilder();
    }

    public static PubRelBuilder pubRel() {
        return new PubRelBuilder();
    }

    public static PubCompBuilder pubComp() {
        return new PubCompBuilder();
    }

    public static ClientPacket.PingReq pingReq() {
        return new ClientPacket.PingReq();
    }

    public static DisconnectBuilder disconnect() {
        return new DisconnectBuilder();
    }

    public static Subscription subscription(String topicFilter) {
        return new Subscription(
                topicFilter,
                QoS.AT_MOST_ONCE.value(),
                false,
                false,
                Subscription.RetainHandling.SEND_AT_SUBSCRIBE);
    }

    public static final class ConnectBuilder {

        private String clientId = DEFAULT_CLIENT_ID;
        private boolean cleanStart = true;
        private int keepAlive = 60;
        private WillMessage will;
        private String username;
        private byte[] password;
        private ConnectProperties properties = ConnectProperties.empty();

        private ConnectBuilder() {
        }

        public ConnectBuilder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public ConnectBuilder cleanStart(boolean cleanStart) {
            this.cleanStart = cleanStart;
            return this;
        }

        public ConnectBuilder keepAlive(int keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public ConnectBuilder will(WillMessage will) {
            this.will = will;
            return this;
        }

        public ConnectBuilder username(String username) {
            this.username = username;
            return this;
        }

        public ConnectBuilder password(byte[] password) {
            this.password = copy(password);
            return this;
        }

        public ConnectBuilder credentials(String username, byte[] password) {
            this.username = username;
            this.password = copy(password);
            return this;
        }

        public ConnectBuilder properties(ConnectProperties properties) {
            this.properties = properties;
            return this;
        }

        public ClientPacket.Connect build() {
            return new ClientPacket.Connect(
                    clientId,
                    cleanStart,
                    keepAlive,
                    will,
                    username,
                    copy(password),
                    properties);
        }
    }

    public static final class PublishBuilder {

        private String topicName = DEFAULT_TOPIC_NAME;
        private QoS qos = QoS.AT_MOST_ONCE;
        private boolean retain;
        private boolean dup;
        private int packetId;
        private Payload payload = new Payload(new byte[0]);
        private PublishProperties properties = PublishProperties.empty();

        private PublishBuilder() {
        }

        public PublishBuilder topicName(String topicName) {
            this.topicName = topicName;
            return this;
        }

        public PublishBuilder qos(QoS qos) {
            this.qos = qos;
            return this;
        }

        public PublishBuilder retain(boolean retain) {
            this.retain = retain;
            return this;
        }

        public PublishBuilder dup(boolean dup) {
            this.dup = dup;
            return this;
        }

        public PublishBuilder packetId(int packetId) {
            this.packetId = packetId;
            return this;
        }

        public PublishBuilder payload(byte[] payload) {
            this.payload = new Payload(copy(payload));
            return this;
        }

        public PublishBuilder payload(Payload payload) {
            this.payload = copy(payload);
            return this;
        }

        public PublishBuilder properties(PublishProperties properties) {
            this.properties = properties;
            return this;
        }

        public ClientPacket.Publish build() {
            return new ClientPacket.Publish(
                    topicName,
                    qos,
                    retain,
                    dup,
                    packetId,
                    copy(payload),
                    properties);
        }
    }

    public static final class SubscribeBuilder {

        private int packetId = 1;
        private List<Subscription> subscriptions = List.of(subscription(DEFAULT_TOPIC_FILTER));
        private SubscribeProperties properties = SubscribeProperties.empty();

        private SubscribeBuilder() {
        }

        public SubscribeBuilder packetId(int packetId) {
            this.packetId = packetId;
            return this;
        }

        public SubscribeBuilder subscriptions(List<Subscription> subscriptions) {
            this.subscriptions = List.copyOf(subscriptions);
            return this;
        }

        public SubscribeBuilder subscriptions(Subscription... subscriptions) {
            return subscriptions(Arrays.asList(subscriptions));
        }

        public SubscribeBuilder properties(SubscribeProperties properties) {
            this.properties = properties;
            return this;
        }

        public ClientPacket.Subscribe build() {
            return new ClientPacket.Subscribe(packetId, List.copyOf(subscriptions), properties);
        }
    }

    public static final class UnsubscribeBuilder {

        private int packetId = 1;
        private List<String> topicFilters = List.of(DEFAULT_TOPIC_FILTER);
        private AckProperties properties = AckProperties.empty();

        private UnsubscribeBuilder() {
        }

        public UnsubscribeBuilder packetId(int packetId) {
            this.packetId = packetId;
            return this;
        }

        public UnsubscribeBuilder topicFilters(List<String> topicFilters) {
            this.topicFilters = List.copyOf(topicFilters);
            return this;
        }

        public UnsubscribeBuilder topicFilters(String... topicFilters) {
            return topicFilters(Arrays.asList(topicFilters));
        }

        public UnsubscribeBuilder properties(AckProperties properties) {
            this.properties = properties;
            return this;
        }

        public ClientPacket.Unsubscribe build() {
            return new ClientPacket.Unsubscribe(packetId, List.copyOf(topicFilters), properties);
        }
    }

    public abstract static class AckBuilder<T extends ClientPacket, B extends AckBuilder<T, B>> {

        private int packetId = 1;
        private ReasonCode reasonCode = ReasonCode.SUCCESS;
        private AckProperties properties = AckProperties.empty();

        public final B packetId(int packetId) {
            this.packetId = packetId;
            return self();
        }

        public final B reasonCode(ReasonCode reasonCode) {
            this.reasonCode = reasonCode;
            return self();
        }

        public final B properties(AckProperties properties) {
            this.properties = properties;
            return self();
        }

        protected final int packetId() {
            return packetId;
        }

        protected final ReasonCode reasonCode() {
            return reasonCode;
        }

        protected final AckProperties properties() {
            return properties;
        }

        protected abstract B self();

        public abstract T build();
    }

    public static final class PubAckBuilder extends AckBuilder<ClientPacket.PubAck, PubAckBuilder> {

        private PubAckBuilder() {
        }

        @Override
        protected PubAckBuilder self() {
            return this;
        }

        @Override
        public ClientPacket.PubAck build() {
            return new ClientPacket.PubAck(packetId(), reasonCode(), properties());
        }
    }

    public static final class PubRecBuilder extends AckBuilder<ClientPacket.PubRec, PubRecBuilder> {

        private PubRecBuilder() {
        }

        @Override
        protected PubRecBuilder self() {
            return this;
        }

        @Override
        public ClientPacket.PubRec build() {
            return new ClientPacket.PubRec(packetId(), reasonCode(), properties());
        }
    }

    public static final class PubRelBuilder extends AckBuilder<ClientPacket.PubRel, PubRelBuilder> {

        private PubRelBuilder() {
        }

        @Override
        protected PubRelBuilder self() {
            return this;
        }

        @Override
        public ClientPacket.PubRel build() {
            return new ClientPacket.PubRel(packetId(), reasonCode(), properties());
        }
    }

    public static final class PubCompBuilder extends AckBuilder<ClientPacket.PubComp, PubCompBuilder> {

        private PubCompBuilder() {
        }

        @Override
        protected PubCompBuilder self() {
            return this;
        }

        @Override
        public ClientPacket.PubComp build() {
            return new ClientPacket.PubComp(packetId(), reasonCode(), properties());
        }
    }

    public static final class DisconnectBuilder {

        private ReasonCode reasonCode = ReasonCode.NORMAL_DISCONNECTION;
        private DisconnectProperties properties = DisconnectProperties.empty();

        private DisconnectBuilder() {
        }

        public DisconnectBuilder reasonCode(ReasonCode reasonCode) {
            this.reasonCode = reasonCode;
            return this;
        }

        public DisconnectBuilder properties(DisconnectProperties properties) {
            this.properties = properties;
            return this;
        }

        public ClientPacket.Disconnect build() {
            return new ClientPacket.Disconnect(reasonCode, properties);
        }
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }

    private static Payload copy(Payload payload) {
        return payload == null ? null : new Payload(copy(payload.data()), payload.formatIndicator());
    }
}

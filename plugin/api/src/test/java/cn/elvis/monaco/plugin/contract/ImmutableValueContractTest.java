package cn.elvis.monaco.plugin.contract;

import cn.elvis.monaco.plugin.api.context.MessageOrigin;
import cn.elvis.monaco.plugin.api.context.PluginPrincipal;
import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.event.PluginEvent;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import cn.elvis.monaco.plugin.api.model.PluginPayload;
import cn.elvis.monaco.plugin.api.model.PluginSecret;
import cn.elvis.monaco.protocol.model.ClientId;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.model.TopicFilter;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImmutableValueContractTest {

    @Test
    void payloadCopiesInputAndOutput() {
        byte[] source = {1, 2, 3};
        PluginPayload payload = PluginPayload.of(source, PluginPayload.Format.UTF8);
        source[0] = 9;

        byte[] copy = payload.copyBytes();
        copy[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, payload.copyBytes());
        assertThrows(ReadOnlyBufferException.class, () -> payload.asReadOnlyBuffer().put((byte) 4));
        assertFalse(payload.toString().contains("1, 2, 3"));
    }

    @Test
    void secretIsCopiedAndRedacted() {
        byte[] source = {10, 11};
        PluginSecret secret = PluginSecret.of(source);
        source[0] = 99;

        assertArrayEquals(new byte[]{10, 11}, secret.copyBytes());
        assertFalse(secret.toString().contains("10"));
        assertThrows(ReadOnlyBufferException.class, () -> secret.asReadOnlyBuffer().put((byte) 3));
    }

    @Test
    void requestContextCopiesAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("tenant", "one");
        PluginRequestContext context = new PluginRequestContext(
                "invocation-1",
                new ConnectionId("connection-1"),
                new ClientId("client-1"),
                Optional.empty(),
                "tcp",
                "127.0.0.1:10000",
                Optional.empty(),
                MessageOrigin.CLIENT,
                "trace-1",
                attributes,
                Instant.EPOCH);
        attributes.clear();

        assertEquals(Map.of("tenant", "one"), context.attributes());
        assertThrows(UnsupportedOperationException.class, () -> context.attributes().put("x", "y"));
    }

    @Test
    void authenticationTypesRedactAttributeValues() {
        PluginPrincipal principal = new PluginPrincipal(
                "alice", Set.of("user"), Map.of("token", "principal-secret"));
        AuthenticationRequest request = new AuthenticationRequest(
                Optional.of("alice"), PluginSecret.of(new byte[]{1}),
                Map.of("credential", "request-secret"));

        assertFalse(principal.toString().contains("principal-secret"));
        assertFalse(request.toString().contains("request-secret"));
    }

    @Test
    void eventCopiesTopicLists() {
        List<TopicFilter> added = new ArrayList<>();
        added.add(new TopicFilter("a/b"));
        PluginEvent.SubscriptionChanged event = new PluginEvent.SubscriptionChanged(
                "event-1", Instant.EPOCH, new ClientId("client-1"), added, List.of());
        added.clear();

        assertEquals(List.of(new TopicFilter("a/b")), event.added());
        assertThrows(UnsupportedOperationException.class,
                () -> event.added().add(new TopicFilter("c/d")));
    }
}

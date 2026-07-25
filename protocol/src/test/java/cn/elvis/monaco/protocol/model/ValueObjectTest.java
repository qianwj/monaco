package cn.elvis.monaco.protocol.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueObjectTest {

    // --- ClientId ---

    @Test
    void clientIdAllowsEmpty() {
        var id = new ClientId("");
        assertTrue(id.isEmpty());
    }

    @Test
    void clientIdRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new ClientId(null));
    }

    @Test
    void clientIdNormalValue() {
        var id = new ClientId("my-client");
        assertEquals("my-client", id.value());
        assertFalse(id.isEmpty());
    }

    // --- PacketId ---

    @Test
    void packetIdValidRange() {
        assertEquals(1, new PacketId(1).value());
        assertEquals(65535, new PacketId(65535).value());
    }

    @Test
    void packetIdRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> new PacketId(0));
    }

    @Test
    void packetIdRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new PacketId(-1));
    }

    @Test
    void packetIdRejectsOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new PacketId(65536));
    }

    // --- ConnectionId ---

    @Test
    void connectionIdGenerate() {
        var id = ConnectionId.generate();
        assertNotNull(id.value());
        assertFalse(id.value().isEmpty());
    }

    @Test
    void connectionIdRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionId(""));
    }

    // --- TopicName ---

    @Test
    void topicNameRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new TopicName(null));
    }

    @Test
    void topicNameRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new TopicName(""));
    }

    @Test
    void topicNameLevels() {
        assertArrayEquals(new String[]{"a", "b", "c"}, new TopicName("a/b/c").levels());
    }

    // --- TopicFilter ---

    @Test
    void topicFilterWildcard() {
        assertTrue(new TopicFilter("a/+/c").isWildcard());
        assertTrue(new TopicFilter("a/#").isWildcard());
        assertFalse(new TopicFilter("a/b/c").isWildcard());
    }

    @Test
    void topicFilterSharedSubscription() {
        var filter = new TopicFilter("$share/group1/a/b/c");
        assertTrue(filter.isShared());
        assertEquals("group1", filter.shareGroup().orElse(null));
        assertEquals("a/b/c", filter.actualFilter());
    }

    @Test
    void topicFilterNotShared() {
        var filter = new TopicFilter("a/b/c");
        assertFalse(filter.isShared());
        assertTrue(filter.shareGroup().isEmpty());
        assertEquals("a/b/c", filter.actualFilter());
    }

    // --- ShareGroup ---

    @Test
    void shareGroupRejectsWildcards() {
        assertThrows(IllegalArgumentException.class, () -> new ShareGroup("group+"));
        assertThrows(IllegalArgumentException.class, () -> new ShareGroup("group#"));
    }

    @Test
    void shareGroupRejectsSlash() {
        assertThrows(IllegalArgumentException.class, () -> new ShareGroup("group/name"));
    }

    @Test
    void shareGroupRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new ShareGroup(""));
    }

    // --- Payload ---

    @Test
    void payloadDefaults() {
        var p = new Payload(null);
        assertTrue(p.isEmpty());
        assertEquals(0, p.size());
    }

    @Test
    void payloadWithData() {
        var p = new Payload(new byte[]{1, 2, 3});
        assertFalse(p.isEmpty());
        assertEquals(3, p.size());
    }
}

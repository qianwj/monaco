package cn.elvis.monaco.plugin.contract;

import cn.elvis.monaco.plugin.api.support.PluginConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigTest {

    @Test
    void readsTypedValuesAndCopiesInput() {
        Map<String, String> values = new HashMap<>();
        values.put("workers", "4");
        values.put("enabled", "true");
        values.put("timeout", "PT0.25S");
        values.put("password", "secret-value");

        PluginConfig config = PluginConfig.of(values);
        values.clear();

        assertEquals(4, config.getInt("workers").orElseThrow());
        assertTrue(config.getBoolean("enabled").orElseThrow());
        assertEquals(Duration.ofMillis(250), config.getDuration("timeout").orElseThrow());
        assertEquals("secret-value", config.require("password"));
        assertFalse(config.toString().contains("secret-value"));
    }

    @Test
    void reportsMissingAndMalformedValues() {
        PluginConfig config = PluginConfig.of(Map.of("count", "many", "enabled", "sometimes"));

        assertThrows(IllegalArgumentException.class, () -> config.require("missing"));
        assertThrows(IllegalArgumentException.class, () -> config.getInt("count"));
        assertThrows(IllegalArgumentException.class, () -> config.getBoolean("enabled"));
        assertTrue(config.get("missing").isEmpty());
    }
}

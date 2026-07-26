package cn.elvis.monaco.plugin.api.support;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Immutable, flattened plugin configuration. Nested keys use dot notation.
 * The string representation intentionally excludes values because they may
 * contain secrets resolved by the runtime.
 */
public final class PluginConfig {

    private static final PluginConfig EMPTY = new PluginConfig(Map.of());

    private final Map<String, String> values;

    private PluginConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
        this.values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Plugin config keys must not be blank");
            }
            Objects.requireNonNull(value, "Plugin config value for " + key);
        });
    }

    public static PluginConfig empty() {
        return EMPTY;
    }

    public static PluginConfig of(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        return values.isEmpty() ? EMPTY : new PluginConfig(values);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(requireKey(key)));
    }

    public String require(String key) {
        String normalizedKey = requireKey(key);
        String value = values.get(normalizedKey);
        if (value == null) {
            throw new IllegalArgumentException("Missing required plugin config: " + normalizedKey);
        }
        return value;
    }

    public OptionalInt getInt(String key) {
        return get(key).map(value -> {
            try {
                return OptionalInt.of(Integer.parseInt(value));
            } catch (NumberFormatException exception) {
                throw invalidValue(key, "integer", exception);
            }
        }).orElseGet(OptionalInt::empty);
    }

    public OptionalLong getLong(String key) {
        return get(key).map(value -> {
            try {
                return OptionalLong.of(Long.parseLong(value));
            } catch (NumberFormatException exception) {
                throw invalidValue(key, "long", exception);
            }
        }).orElseGet(OptionalLong::empty);
    }

    public Optional<Boolean> getBoolean(String key) {
        return get(key).map(value -> switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw invalidValue(key, "boolean", null);
        });
    }

    public Optional<Duration> getDuration(String key) {
        return get(key).map(value -> {
            try {
                return Duration.parse(value);
            } catch (DateTimeParseException exception) {
                throw invalidValue(key, "ISO-8601 duration", exception);
            }
        });
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Plugin config key must not be blank");
        }
        return key;
    }

    private static IllegalArgumentException invalidValue(String key, String expected, Exception cause) {
        return new IllegalArgumentException(
                "Plugin config '" + key + "' must be a valid " + expected,
                cause);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PluginConfig config && values.equals(config.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "PluginConfig[keys=" + values.keySet() + ']';
    }
}

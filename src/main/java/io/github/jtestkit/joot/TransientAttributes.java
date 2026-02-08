package io.github.jtestkit.joot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed container for transient (non-persisted) attributes.
 * Transient attributes are not stored in the database but are available
 * to lifecycle callbacks via {@link TransientAwareCallback}.
 *
 * @since 0.10.0
 */
public class TransientAttributes {

    private final Map<String, Object> values;

    TransientAttributes(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Gets a transient attribute value by name.
     *
     * @param name the attribute name
     * @param type the expected type
     * @param <T> the value type
     * @return the value, or null if not set
     * @throws ClassCastException if the value is not of the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String name, Class<T> type) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    /**
     * Gets a transient attribute value with a default.
     *
     * @param name the attribute name
     * @param type the expected type
     * @param defaultValue value returned if attribute is not set
     * @param <T> the value type
     * @return the value, or defaultValue if not set
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String name, Class<T> type, T defaultValue) {
        Object value = values.get(name);
        if (value == null) {
            return defaultValue;
        }
        return type.cast(value);
    }

    /**
     * Checks if a transient attribute is set.
     */
    public boolean has(String name) {
        return values.containsKey(name);
    }

    /**
     * Returns all transient attributes as an unmodifiable map.
     */
    public Map<String, Object> asMap() {
        return values;
    }

    static TransientAttributes empty() {
        return new TransientAttributes(Collections.emptyMap());
    }
}

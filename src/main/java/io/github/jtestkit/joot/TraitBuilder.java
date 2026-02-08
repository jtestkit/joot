package io.github.jtestkit.joot;

import org.jooq.Field;
import org.jooq.Record;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for defining trait overrides and callbacks.
 * Used as the parameter type in {@code f.trait("name", t -> { ... })}.
 *
 * @param <R> the Record type
 */
public class TraitBuilder<R extends Record> {

    private final Map<Field<?>, Object> overrides = new LinkedHashMap<>();
    private final Map<Field<?>, ValueGenerator<?>> generators = new LinkedHashMap<>();
    private final List<Consumer<Record>> beforeCreateCallbacks = new ArrayList<>();
    private final List<Consumer<Record>> afterCreateCallbacks = new ArrayList<>();
    private final List<TransientAwareCallback> transientBeforeCreateCallbacks = new ArrayList<>();
    private final List<TransientAwareCallback> transientAfterCreateCallbacks = new ArrayList<>();

    /**
     * Sets a field override for this trait.
     */
    public <T> TraitBuilder<R> set(Field<T> field, T value) {
        overrides.put(field, value);
        return this;
    }

    /**
     * Sets a generator override for this trait.
     */
    public <T> TraitBuilder<R> withGenerator(Field<T> field, ValueGenerator<T> generator) {
        generators.put(field, generator);
        return this;
    }

    /**
     * Registers a callback to execute before INSERT.
     */
    public TraitBuilder<R> beforeCreate(Consumer<Record> callback) {
        beforeCreateCallbacks.add(callback);
        return this;
    }

    /**
     * Registers a callback to execute after INSERT.
     */
    public TraitBuilder<R> afterCreate(Consumer<Record> callback) {
        afterCreateCallbacks.add(callback);
        return this;
    }

    /**
     * Registers a transient-aware callback to execute before INSERT.
     */
    public TraitBuilder<R> beforeCreate(TransientAwareCallback callback) {
        transientBeforeCreateCallbacks.add(callback);
        return this;
    }

    /**
     * Registers a transient-aware callback to execute after INSERT.
     */
    public TraitBuilder<R> afterCreate(TransientAwareCallback callback) {
        transientAfterCreateCallbacks.add(callback);
        return this;
    }

    /**
     * Builds the immutable Trait. Package-private.
     */
    Trait<R> build(String name) {
        return new Trait<>(name, overrides, generators, beforeCreateCallbacks, afterCreateCallbacks,
                transientBeforeCreateCallbacks, transientAfterCreateCallbacks);
    }
}

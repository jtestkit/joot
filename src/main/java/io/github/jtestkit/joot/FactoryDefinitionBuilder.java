package io.github.jtestkit.joot;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for defining factory defaults, traits, and callbacks.
 * Used as the parameter type in {@code ctx.define(TABLE, f -> { ... })}.
 *
 * @param <R> the Record type
 */
public class FactoryDefinitionBuilder<R extends Record> {

    private final Map<Field<?>, Object> defaultValues = new LinkedHashMap<>();
    private final Map<Field<?>, ValueGenerator<?>> generators = new LinkedHashMap<>();
    private final Map<String, Trait<R>> traits = new LinkedHashMap<>();
    private final List<Consumer<Record>> beforeCreateCallbacks = new ArrayList<>();
    private final List<Consumer<Record>> afterCreateCallbacks = new ArrayList<>();
    private final List<TransientAwareCallback> transientBeforeCreateCallbacks = new ArrayList<>();
    private final List<TransientAwareCallback> transientAfterCreateCallbacks = new ArrayList<>();
    private String parentName;

    /**
     * Sets a default value for a field.
     */
    public <T> FactoryDefinitionBuilder<R> set(Field<T> field, T value) {
        defaultValues.put(field, value);
        return this;
    }

    /**
     * Sets a generator for a field within this definition.
     */
    public <T> FactoryDefinitionBuilder<R> withGenerator(Field<T> field, ValueGenerator<T> generator) {
        generators.put(field, generator);
        return this;
    }

    /**
     * Defines a named trait (variation) for this factory.
     */
    public FactoryDefinitionBuilder<R> trait(String name, Consumer<TraitBuilder<R>> traitConfig) {
        TraitBuilder<R> traitBuilder = new TraitBuilder<>();
        traitConfig.accept(traitBuilder);
        traits.put(name, traitBuilder.build(name));
        return this;
    }

    /**
     * Registers a callback to execute before INSERT.
     */
    public FactoryDefinitionBuilder<R> beforeCreate(Consumer<Record> callback) {
        beforeCreateCallbacks.add(callback);
        return this;
    }

    /**
     * Sets the parent factory name for inheritance.
     * The child inherits defaults, generators, traits, and callbacks from the parent.
     * Child values override parent values.
     */
    public FactoryDefinitionBuilder<R> parent(String parentName) {
        this.parentName = parentName;
        return this;
    }

    /**
     * Registers a callback to execute after INSERT.
     */
    public FactoryDefinitionBuilder<R> afterCreate(Consumer<Record> callback) {
        afterCreateCallbacks.add(callback);
        return this;
    }

    /**
     * Registers a transient-aware callback to execute before INSERT.
     */
    public FactoryDefinitionBuilder<R> beforeCreate(TransientAwareCallback callback) {
        transientBeforeCreateCallbacks.add(callback);
        return this;
    }

    /**
     * Registers a transient-aware callback to execute after INSERT.
     */
    public FactoryDefinitionBuilder<R> afterCreate(TransientAwareCallback callback) {
        transientAfterCreateCallbacks.add(callback);
        return this;
    }

    /**
     * Builds the immutable FactoryDefinition. Package-private.
     */
    FactoryDefinition<R> build(Table<R> table) {
        return new FactoryDefinition<>(table, parentName, defaultValues, generators, traits,
                beforeCreateCallbacks, afterCreateCallbacks,
                transientBeforeCreateCallbacks, transientAfterCreateCallbacks);
    }
}

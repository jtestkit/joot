package io.github.jtestkit.joot;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reusable factory definition for a jOOQ table.
 * Stores default field values, per-field generators, traits, and lifecycle callbacks.
 * <p>
 * Immutable once built via {@link FactoryDefinitionBuilder}.
 *
 * @param <R> the Record type
 */
class FactoryDefinition<R extends Record> {

    private final Table<R> table;
    private final String parentName;
    private final Map<Field<?>, Object> defaultValues;
    private final Map<Field<?>, ValueGenerator<?>> generators;
    private final Map<String, Trait<R>> traits;
    private final List<Consumer<Record>> beforeCreateCallbacks;
    private final List<Consumer<Record>> afterCreateCallbacks;
    private final List<TransientAwareCallback> transientBeforeCreateCallbacks;
    private final List<TransientAwareCallback> transientAfterCreateCallbacks;

    FactoryDefinition(Table<R> table,
                      String parentName,
                      Map<Field<?>, Object> defaultValues,
                      Map<Field<?>, ValueGenerator<?>> generators,
                      Map<String, Trait<R>> traits,
                      List<Consumer<Record>> beforeCreateCallbacks,
                      List<Consumer<Record>> afterCreateCallbacks,
                      List<TransientAwareCallback> transientBeforeCreateCallbacks,
                      List<TransientAwareCallback> transientAfterCreateCallbacks) {
        this.table = table;
        this.parentName = parentName;
        this.defaultValues = Collections.unmodifiableMap(new LinkedHashMap<>(defaultValues));
        this.generators = Collections.unmodifiableMap(new LinkedHashMap<>(generators));
        this.traits = Collections.unmodifiableMap(new LinkedHashMap<>(traits));
        this.beforeCreateCallbacks = Collections.unmodifiableList(new ArrayList<>(beforeCreateCallbacks));
        this.afterCreateCallbacks = Collections.unmodifiableList(new ArrayList<>(afterCreateCallbacks));
        this.transientBeforeCreateCallbacks = Collections.unmodifiableList(new ArrayList<>(transientBeforeCreateCallbacks));
        this.transientAfterCreateCallbacks = Collections.unmodifiableList(new ArrayList<>(transientAfterCreateCallbacks));
    }

    Table<R> getTable() {
        return table;
    }

    String getParentName() {
        return parentName;
    }

    Map<Field<?>, Object> getDefaultValues() {
        return defaultValues;
    }

    Map<Field<?>, ValueGenerator<?>> getGenerators() {
        return generators;
    }

    Map<String, Trait<R>> getTraits() {
        return traits;
    }

    List<Consumer<Record>> getBeforeCreateCallbacks() {
        return beforeCreateCallbacks;
    }

    List<Consumer<Record>> getAfterCreateCallbacks() {
        return afterCreateCallbacks;
    }

    List<TransientAwareCallback> getTransientBeforeCreateCallbacks() {
        return transientBeforeCreateCallbacks;
    }

    List<TransientAwareCallback> getTransientAfterCreateCallbacks() {
        return transientAfterCreateCallbacks;
    }

    /**
     * Resolves default values by merging base defaults with trait overrides.
     * Traits are applied in order; later traits override earlier ones.
     *
     * @param traitNames ordered list of trait names to apply
     * @return merged map of field -> value
     */
    Map<Field<?>, Object> resolveDefaults(List<String> traitNames) {
        Map<Field<?>, Object> resolved = new LinkedHashMap<>(defaultValues);
        for (String traitName : traitNames) {
            Trait<R> trait = traits.get(traitName);
            if (trait != null) {
                resolved.putAll(trait.getOverrides());
            }
        }
        return resolved;
    }

    /**
     * Resolves generators by merging base generators with trait generators.
     */
    Map<Field<?>, ValueGenerator<?>> resolveGenerators(List<String> traitNames) {
        Map<Field<?>, ValueGenerator<?>> resolved = new LinkedHashMap<>(generators);
        for (String traitName : traitNames) {
            Trait<R> trait = traits.get(traitName);
            if (trait != null) {
                resolved.putAll(trait.getGenerators());
            }
        }
        return resolved;
    }

    /**
     * Resolves beforeCreate callbacks: base callbacks + trait callbacks in order.
     */
    List<Consumer<Record>> resolveBeforeCreateCallbacks(List<String> traitNames) {
        List<Consumer<Record>> resolved = new ArrayList<>(beforeCreateCallbacks);
        for (String traitName : traitNames) {
            Trait<R> trait = traits.get(traitName);
            if (trait != null) {
                resolved.addAll(trait.getBeforeCreateCallbacks());
            }
        }
        return resolved;
    }

    /**
     * Resolves afterCreate callbacks: base callbacks + trait callbacks in order.
     */
    List<Consumer<Record>> resolveAfterCreateCallbacks(List<String> traitNames) {
        List<Consumer<Record>> resolved = new ArrayList<>(afterCreateCallbacks);
        for (String traitName : traitNames) {
            Trait<R> trait = traits.get(traitName);
            if (trait != null) {
                resolved.addAll(trait.getAfterCreateCallbacks());
            }
        }
        return resolved;
    }

    /**
     * Resolves transient-aware beforeCreate callbacks: base + trait callbacks in order.
     */
    List<TransientAwareCallback> resolveTransientBeforeCreateCallbacks(List<String> traitNames) {
        List<TransientAwareCallback> resolved = new ArrayList<>(transientBeforeCreateCallbacks);
        for (String traitName : traitNames) {
            Trait<R> trait = traits.get(traitName);
            if (trait != null) {
                resolved.addAll(trait.getTransientBeforeCreateCallbacks());
            }
        }
        return resolved;
    }

    /**
     * Resolves transient-aware afterCreate callbacks: base + trait callbacks in order.
     */
    List<TransientAwareCallback> resolveTransientAfterCreateCallbacks(List<String> traitNames) {
        List<TransientAwareCallback> resolved = new ArrayList<>(transientAfterCreateCallbacks);
        for (String traitName : traitNames) {
            Trait<R> trait = traits.get(traitName);
            if (trait != null) {
                resolved.addAll(trait.getTransientAfterCreateCallbacks());
            }
        }
        return resolved;
    }

    /**
     * Checks if a trait with the given name exists.
     */
    boolean hasTrait(String name) {
        return traits.containsKey(name);
    }

    /**
     * Returns the names of all defined traits.
     */
    java.util.Set<String> getTraitNames() {
        return traits.keySet();
    }
}

package io.github.jtestkit.joot;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Registry for factory definitions, keyed by table name or custom name.
 * Supports factory inheritance via parent chain resolution.
 * Thread-safe via ConcurrentHashMap (same pattern as {@link GeneratorRegistry}).
 */
class FactoryDefinitionRegistry {

    private final ConcurrentHashMap<String, FactoryDefinition<?>> definitions = new ConcurrentHashMap<>();

    /**
     * Registers a definition for a table (keyed by table name).
     */
    <R extends Record> void register(Table<R> table, FactoryDefinition<R> definition) {
        definitions.put(table.getName().toLowerCase(), definition);
    }

    /**
     * Registers a named definition.
     */
    <R extends Record> void register(String name, FactoryDefinition<R> definition) {
        definitions.put(name.toLowerCase(), definition);
    }

    /**
     * Resolves a definition for a table with inheritance.
     * If the definition has a parent, merges parent chain (parent first, child overrides).
     * Returns null if none registered.
     */
    @SuppressWarnings("unchecked")
    <R extends Record> FactoryDefinition<R> resolve(Table<R> table) {
        FactoryDefinition<R> def = (FactoryDefinition<R>) definitions.get(table.getName().toLowerCase());
        if (def == null) {
            return null;
        }
        if (def.getParentName() == null) {
            return def;
        }
        return mergeWithParent(def);
    }

    /**
     * Resolves a named definition with inheritance.
     */
    @SuppressWarnings("unchecked")
    <R extends Record> FactoryDefinition<R> resolveByName(String name) {
        FactoryDefinition<R> def = (FactoryDefinition<R>) definitions.get(name.toLowerCase());
        if (def == null) {
            return null;
        }
        if (def.getParentName() == null) {
            return def;
        }
        return mergeWithParent(def);
    }

    /**
     * Merges child definition with its parent chain.
     * Parent defaults come first, child overrides on top.
     * Callbacks are concatenated (parent first, then child).
     * Traits are merged (child traits override parent traits with same name).
     */
    @SuppressWarnings("unchecked")
    private <R extends Record> FactoryDefinition<R> mergeWithParent(FactoryDefinition<R> child) {
        Set<String> visited = new HashSet<>();
        return mergeWithParent(child, visited);
    }

    @SuppressWarnings("unchecked")
    private <R extends Record> FactoryDefinition<R> mergeWithParent(FactoryDefinition<R> child, Set<String> visited) {
        String parentKey = child.getParentName().toLowerCase();
        if (!visited.add(parentKey)) {
            throw new IllegalStateException(
                "Circular factory inheritance detected: '" + child.getParentName() + "' forms a cycle");
        }
        FactoryDefinition<R> parent = (FactoryDefinition<R>) definitions.get(parentKey);
        if (parent == null) {
            throw new IllegalStateException(
                "Parent factory definition '" + child.getParentName() + "' not found. " +
                "Register it with ctx.define(\"" + child.getParentName() + "\", ...) before referencing it.");
        }
        // Resolve parent recursively
        if (parent.getParentName() != null) {
            parent = mergeWithParent(parent, visited);
        }

        // Merge defaults: parent first, child overrides
        Map<Field<?>, Object> mergedDefaults = new LinkedHashMap<>(parent.getDefaultValues());
        mergedDefaults.putAll(child.getDefaultValues());

        // Merge generators: parent first, child overrides
        Map<Field<?>, ValueGenerator<?>> mergedGenerators = new LinkedHashMap<>(parent.getGenerators());
        mergedGenerators.putAll(child.getGenerators());

        // Merge traits: parent first, child overrides by name
        Map<String, Trait<R>> mergedTraits = new LinkedHashMap<>(parent.getTraits());
        mergedTraits.putAll(child.getTraits());

        // Concatenate callbacks: parent first, then child
        List<Consumer<Record>> mergedBefore = new ArrayList<>(parent.getBeforeCreateCallbacks());
        mergedBefore.addAll(child.getBeforeCreateCallbacks());

        List<Consumer<Record>> mergedAfter = new ArrayList<>(parent.getAfterCreateCallbacks());
        mergedAfter.addAll(child.getAfterCreateCallbacks());

        // Concatenate transient-aware callbacks
        List<TransientAwareCallback> mergedTransientBefore = new ArrayList<>(parent.getTransientBeforeCreateCallbacks());
        mergedTransientBefore.addAll(child.getTransientBeforeCreateCallbacks());

        List<TransientAwareCallback> mergedTransientAfter = new ArrayList<>(parent.getTransientAfterCreateCallbacks());
        mergedTransientAfter.addAll(child.getTransientAfterCreateCallbacks());

        return new FactoryDefinition<>(child.getTable(), null, mergedDefaults, mergedGenerators,
                mergedTraits, mergedBefore, mergedAfter, mergedTransientBefore, mergedTransientAfter);
    }
}

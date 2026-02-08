package io.github.jtestkit.joot;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.TableField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Default implementation of RecordBuilder.
 * Creates jOOQ Record entities by generating values for NOT NULL fields and inserting into database.
 * Automatically creates parent entities for foreign key dependencies.
 * 
 * @param <R> the Record type
 * @since 0.1.0
 */
class RecordBuilderImpl<R extends Record> implements RecordBuilder<R> {
    
    private final DSLContext dsl;
    private final Table<R> table;
    private final JootContext jootContext;
    private final MetadataAnalyzer metadataAnalyzer;
    private final CreationChain creationChain;
    private final Map<Field<?>, Object> explicitValues = new HashMap<>();
    private final Map<Field<?>, ValueGenerator<?>> perBuilderGenerators = new HashMap<>();
    private final List<String> activeTraits = new ArrayList<>();
    private final Map<String, Object> transientAttrs = new HashMap<>();
    private boolean shouldGenerateNullables;  // Can be overridden via generateNullables()
    
    RecordBuilderImpl(DSLContext dsl, Table<R> table,
                      JootContext jootContext,
                      CreationChain creationChain,
                      boolean generateNullables) {
        this.dsl = dsl;
        this.table = table;
        this.jootContext = jootContext;
        this.metadataAnalyzer = new MetadataAnalyzer();
        this.creationChain = creationChain;
        this.shouldGenerateNullables = generateNullables;
    }
    
    @Override
    public <T> RecordBuilder<R> set(Field<T> field, T value) {
        explicitValues.put(field, value);
        return this;
    }
    
    @Override
    public RecordBuilder<R> generateNullables(boolean generate) {
        this.shouldGenerateNullables = generate;
        return this;
    }
    
    @Override
    public <T> RecordBuilder<R> withGenerator(Field<T> field, ValueGenerator<T> generator) {
        perBuilderGenerators.put(field, generator);
        return this;
    }

    @Override
    public RecordBuilder<R> trait(String traitName) {
        activeTraits.add(traitName);
        return this;
    }

    @Override
    public RecordBuilder<R> transientAttr(String name, Object value) {
        transientAttrs.put(name, value);
        return this;
    }

    @Override
    public List<R> times(int count) {
        List<R> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(cloneConfiguration().build());
        }
        return results;
    }

    @Override
    public List<R> times(int count, BiConsumer<RecordBuilder<R>, Integer> customizer) {
        List<R> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RecordBuilderImpl<R> fresh = cloneConfiguration();
            customizer.accept(fresh, i);
            results.add(fresh.build());
        }
        return results;
    }

    /**
     * Creates a fresh builder with the same configuration.
     * Each clone gets its own copy of explicitValues so build() doesn't leak state.
     */
    private RecordBuilderImpl<R> cloneConfiguration() {
        RecordBuilderImpl<R> clone = new RecordBuilderImpl<>(dsl, table, jootContext, creationChain, shouldGenerateNullables);
        clone.explicitValues.putAll(this.explicitValues);
        clone.perBuilderGenerators.putAll(this.perBuilderGenerators);
        clone.activeTraits.addAll(this.activeTraits);
        clone.transientAttrs.putAll(this.transientAttrs);
        return clone;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public R build() {
        // 0. Resolve factory definition defaults and trait overrides
        List<Consumer<Record>> beforeCallbacks = resolveDefinitionDefaults();

        // 1. Auto-create parent entities for FK fields that are not explicitly set
        // This may leave some FKs as NULL if they're part of cyclic dependency
        autoCreateForeignKeyDependencies();
        
        // 2. Create new Record
        R record = dsl.newRecord(table);
        
        // 3. Fill fields with generated or explicit values
        for (Field<?> field : table.fields()) {
            if (explicitValues.containsKey(field)) {
                // Use explicit value (even if null)
                setField(record, field, explicitValues.get(field));
            } else if (shouldSkipGeneration(field)) {
                // Skip fields with database-generated values (identity, default, etc)
                // Database will generate these automatically
                continue;
            } else if (!field.getDataType().nullable()) {
                // NOT NULL fields must be generated
                if (metadataAnalyzer.isForeignKeyField(field, table)) {
                    throw new IllegalStateException(
                        "FK field " + field.getName() + " should have been auto-created but wasn't"
                    );
                }
                Object value = generateDefaultValue(field);
                setField(record, field, value);
            } else if (shouldGenerateNullables) {
                // Nullable field + generateNullables() enabled
                Object value = generateDefaultValue(field);
                setField(record, field, value);
            }
            // else: nullable field without generateNullables() - leave as null
        }
        
        // 4. Execute beforeCreate callbacks
        TransientAttributes transients = new TransientAttributes(transientAttrs);
        for (Consumer<Record> callback : beforeCallbacks) {
            callback.accept(record);
        }
        for (TransientAwareCallback callback : resolveTransientBeforeCreateCallbacks()) {
            callback.accept(record, transients);
        }

        // 5. INSERT into database and get the inserted record back with generated PK
        R insertedRecord = dsl.insertInto(table)
            .set(record)
            .returning()
            .fetchOne();

        // 6. Execute afterCreate callbacks
        List<Consumer<Record>> afterCallbacks = resolveAfterCreateCallbacks();
        for (Consumer<Record> callback : afterCallbacks) {
            callback.accept(insertedRecord);
        }
        for (TransientAwareCallback callback : resolveTransientAfterCreateCallbacks()) {
            callback.accept(insertedRecord, transients);
        }

        // 7. Return the Record
        return insertedRecord;
    }
    
    /**
     * Automatically creates parent entities for all FK fields that are NOT explicitly set.
     * Detects circular dependencies and throws CircularDependencyException.
     */
    private void autoCreateForeignKeyDependencies() {
        List<ForeignKey<?, ?>> foreignKeys = metadataAnalyzer.getForeignKeys(table);
        
        // Build effective chain that includes current table (for cycle detection)
        CreationChain effectiveChain = creationChain.add(table);
        
        for (ForeignKey<?, ?> fk : foreignKeys) {
            Field<?> fkField = fk.getFields().get(0);
            
            // Skip if FK value was explicitly set
            if (explicitValues.containsKey(fkField)) {
                continue;
            }
            
            Table<?> parentTable = fk.getKey().getTable();
            
            // Handle special cases: self-reference and cycles
            if (handleSelfReference(fkField, parentTable)) {
                continue;
            }
            
            if (handleCyclicDependency(fkField, parentTable, effectiveChain)) {
                continue;
            }
            
            // Skip nullable FK if generateNullables=false
            if (shouldSkipNullableFK(fkField)) {
                explicitValues.put(fkField, null);
                continue;
            }
            
            // Create parent entity and set FK
            Object parentPkValue = createParentEntity(parentTable, effectiveChain);
            explicitValues.put(fkField, parentPkValue);
        }
    }
    
    /**
     * Handles self-reference (table references itself).
     * 
     * @return true if self-reference was handled, false otherwise
     */
    private boolean handleSelfReference(Field<?> fkField, Table<?> parentTable) {
        if (!parentTable.equals(table)) {
            return false; // Not a self-reference
        }
        
        // Self-reference must be nullable (NOT NULL self-reference is impossible)
        if (!fkField.getDataType().nullable()) {
            throw new IllegalStateException(
                "NOT NULL self-reference is impossible for table: " + table.getName()
            );
        }
        
        // Skip if generateNullables=false (minimalist mode)
        if (!shouldGenerateNullables) {
            return true; // Self-reference handled (left as NULL)
        }
        
        // Create parent with depth=1 (parent has no parent)
        // Force generateNullables=false for parent to avoid infinite recursion
        Record parentRecord = new RecordBuilderImpl<>(
            dsl,
            (Table<R>) parentTable,
            jootContext,
            creationChain,  // Same chain, but generateNullables=false
            false  // Force generateNullables=false for parent (depth=1)
        ).build();
        
        // Extract parent's PK and set as FK
        TableField<?, ?> parentPk = getPrimaryKey(parentTable);
        Object parentPkValue = parentRecord.get(parentPk);
        explicitValues.put(fkField, parentPkValue);
        
        return true; // Self-reference handled
    }
    
    /**
     * Handles circular dependency between different tables.
     * 
     * @return true if cycle was detected and handled, false otherwise
     */
    private boolean handleCyclicDependency(Field<?> fkField, Table<?> parentTable, 
                                           CreationChain effectiveChain) {
        if (!effectiveChain.contains(parentTable)) {
            return false; // No cycle detected
        }
        
        // Circular dependency detected between different tables!
        
        // Strategy:
        // - NOT NULL FK: must be created (can't skip)
        // - Nullable FK: leave as NULL to break cycle (ignore generateNullables)
        
        if (!fkField.getDataType().nullable()) {
            // Current FK is NOT NULL - we MUST create it
            // But first check if cycle can be broken by a nullable FK somewhere
            CyclicDependencyResolver resolver = ((JootContextImpl) jootContext).getCyclicResolver();
            Field<?> nullableFKInCycle = resolver.findNullableFKInCycle(parentTable, table, effectiveChain);
            
            if (nullableFKInCycle == null) {
                // No nullable FK in cycle - unresolvable!
                List<Table<?>> cycleChain = effectiveChain.buildCycleChain(table, parentTable);
                throw CircularDependencyException.fromChain(cycleChain);
            }
            
            // There IS a nullable FK in the cycle that will break it
            // So we can safely create this NOT NULL FK (fall through)
            return false;
        } else {
            // Current FK is nullable - leave as NULL to break cycle
            // (ignore generateNullables setting for cyclic FKs)
            explicitValues.put(fkField, null); // Explicitly set to NULL
            return true; // Cycle handled
        }
    }
    
    /**
     * Checks if nullable FK should be skipped based on generateNullables flag.
     * 
     * @return true if FK should be skipped (left as NULL)
     */
    private boolean shouldSkipNullableFK(Field<?> fkField) {
        return fkField.getDataType().nullable() && !shouldGenerateNullables;
    }
    
    /**
     * Recursively creates parent entity and returns its PK value.
     * Inherits the current builder's generateNullables setting.
     */
    private Object createParentEntity(Table<?> parentTable, CreationChain effectiveChain) {
        // Recursively create parent entity using RecordBuilder (ALWAYS Record!)
        // Propagate current builder's generateNullables setting to parent
        Record parentRecord = new RecordBuilderImpl<>(
            dsl,
            parentTable,
            jootContext,
            effectiveChain, // Pass effective chain for correct cycle detection
            shouldGenerateNullables  // Inherit current builder's setting
        ).build();
        
        // Extract and return parent's PK value
        TableField<?, ?> parentPk = getPrimaryKey(parentTable);
        return parentRecord.get(parentPk);
    }
    
    @SuppressWarnings("unchecked")
    private <T> void setField(Record record, Field<T> field, Object value) {
        record.set(field, (T) value);
    }
    
    /**
     * Gets the primary key field from the table.
     */
    @SuppressWarnings("unchecked")
    private <T extends Record> TableField<T, ?> getPrimaryKey(Table<T> table) {
        if (table.getPrimaryKey() == null || table.getPrimaryKey().getFields().isEmpty()) {
            throw new IllegalStateException("Table " + table.getName() + " has no primary key");
        }
        return (TableField<T, ?>) table.getPrimaryKey().getFields().get(0);
    }
    
    /**
     * Determines if field value generation should be skipped.
     * <p>
     * Skips generation for fields where the database will generate the value:
     * <ul>
     *   <li>Identity columns (SERIAL, AUTO_INCREMENT, IDENTITY)</li>
     *   <li>Fields with DEFAULT values (e.g. DEFAULT CURRENT_TIMESTAMP)</li>
     * </ul>
     * 
     * @param field the field to check
     * @return true if generation should be skipped, false otherwise
     */
    private boolean shouldSkipGeneration(Field<?> field) {
        org.jooq.DataType<?> dataType = field.getDataType();
        
        // Skip identity columns (SERIAL, AUTO_INCREMENT, IDENTITY)
        if (dataType.identity()) {
            return true;
        }
        
        // Skip fields with DEFAULT values
        if (dataType.defaulted()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Generates a default value for a field.
     * <p>
     * Resolution priority:
     * 1. Per-builder generator (.withGenerator())
     * 2. Global generator from GeneratorRegistry (field-specific or type-based)
     * 3. Automatic enum handling (first value from enum.values())
     * <p>
     * All common types (String, Integer, Long, UUID, Boolean, LocalDateTime, LocalDate)
     * have built-in generators pre-registered in GeneratorRegistry.
     * Enums are handled automatically.
     */
    private Object generateDefaultValue(Field<?> field) {
        // Priority 1: Per-builder generator (highest precedence)
        if (perBuilderGenerators.containsKey(field)) {
            ValueGenerator<?> perBuilderGen = perBuilderGenerators.get(field);
            return generateWithCustomGenerator(perBuilderGen, field);
        }
        
        // Priority 2: Global generator from registry (includes built-in generators)
        GeneratorRegistry registry = ((JootContextImpl) jootContext).getGeneratorRegistry();
        ValueGenerator<?> customGenerator = registry.resolve(field);
        
        if (customGenerator != null) {
            // Use generator - call the advanced method with full context
            return generateWithCustomGenerator(customGenerator, field);
        }
        
        // Priority 3: Automatic enum handling
        Class<?> type = field.getType();
        if (type.isEnum()) {
            Object[] enumConstants = type.getEnumConstants();
            if (enumConstants != null && enumConstants.length > 0) {
                return enumConstants[0];  // Always return first enum value (deterministic)
            }
        }
        
        // No generator found
        throw new IllegalArgumentException(
            "Unsupported field type: " + type.getName() + " for field: " + field.getName() +
            ". Register a custom generator via ctx.registerGenerator() or .withGenerator()"
        );
    }
    
    /**
     * Calls custom generator with proper generic types.
     * This method handles the unchecked cast required for wildcard types.
     */
    @SuppressWarnings("unchecked")
    private <T> T generateWithCustomGenerator(ValueGenerator<?> generator, Field<T> field) {
        ValueGenerator<T> typedGenerator = (ValueGenerator<T>) generator;
        return typedGenerator.generate(field, table);
    }

    @Override
    public R buildWithoutInsert() {
        // Resolve factory definition defaults (no callbacks executed)
        resolveDefinitionDefaults();

        // Create and fill Record — no FK auto-creation, no INSERT
        R record = dsl.newRecord(table);
        fillRecord(record);
        return record;
    }

    @Override
    public Map<Field<?>, Object> buildAttributes() {
        // Resolve factory definition defaults
        resolveDefinitionDefaults();

        // Build attribute map for all fields that would be set
        Map<Field<?>, Object> attributes = new HashMap<>();
        for (Field<?> field : table.fields()) {
            if (explicitValues.containsKey(field)) {
                attributes.put(field, explicitValues.get(field));
            } else if (shouldSkipGeneration(field)) {
                continue;
            } else if (!field.getDataType().nullable()) {
                if (!metadataAnalyzer.isForeignKeyField(field, table)) {
                    attributes.put(field, generateDefaultValue(field));
                }
            } else if (shouldGenerateNullables) {
                attributes.put(field, generateDefaultValue(field));
            }
        }
        return attributes;
    }

    /**
     * Fills a Record with generated or explicit values.
     * Extracted to share between build() and buildWithoutInsert().
     */
    private void fillRecord(R record) {
        for (Field<?> field : table.fields()) {
            if (explicitValues.containsKey(field)) {
                setField(record, field, explicitValues.get(field));
            } else if (shouldSkipGeneration(field)) {
                continue;
            } else if (!field.getDataType().nullable()) {
                if (metadataAnalyzer.isForeignKeyField(field, table)) {
                    // In buildWithoutInsert mode, FK fields without explicit values are skipped
                    continue;
                }
                Object value = generateDefaultValue(field);
                setField(record, field, value);
            } else if (shouldGenerateNullables) {
                Object value = generateDefaultValue(field);
                setField(record, field, value);
            }
        }
    }

    /**
     * Resolves factory definition defaults and trait overrides.
     * Merges into explicitValues/perBuilderGenerators via putIfAbsent
     * so explicit .set() calls always win.
     *
     * @return list of beforeCreate callbacks to execute (empty if no definition)
     */
    @SuppressWarnings("unchecked")
    private List<Consumer<Record>> resolveDefinitionDefaults() {
        FactoryDefinitionRegistry registry = ((JootContextImpl) jootContext).getDefinitionRegistry();
        FactoryDefinition<R> def = registry.resolve(table);
        if (def == null) {
            if (!activeTraits.isEmpty()) {
                throw new IllegalArgumentException(
                    "Cannot apply traits — no factory definition registered for table '" +
                    table.getName() + "'. Register one with ctx.define(" + table.getName() + ", ...)");
            }
            return Collections.emptyList();
        }

        // Validate that all requested traits exist
        for (String traitName : activeTraits) {
            if (!def.hasTrait(traitName)) {
                throw new IllegalArgumentException(
                    "Unknown trait '" + traitName + "' for table '" + table.getName() +
                    "'. Available traits: " + def.getTraitNames());
            }
        }

        // Merge definition defaults (trait overrides on top of base)
        Map<Field<?>, Object> resolvedDefaults = def.resolveDefaults(activeTraits);
        for (Map.Entry<Field<?>, Object> entry : resolvedDefaults.entrySet()) {
            explicitValues.putIfAbsent(entry.getKey(), entry.getValue());
        }

        // Merge definition generators
        Map<Field<?>, ValueGenerator<?>> resolvedGenerators = def.resolveGenerators(activeTraits);
        for (Map.Entry<Field<?>, ValueGenerator<?>> entry : resolvedGenerators.entrySet()) {
            perBuilderGenerators.putIfAbsent(entry.getKey(), entry.getValue());
        }

        return def.resolveBeforeCreateCallbacks(activeTraits);
    }

    /**
     * Resolves afterCreate callbacks from definition and active traits.
     */
    private List<Consumer<Record>> resolveAfterCreateCallbacks() {
        FactoryDefinitionRegistry registry = ((JootContextImpl) jootContext).getDefinitionRegistry();
        FactoryDefinition<R> def = registry.resolve(table);
        if (def == null) {
            return Collections.emptyList();
        }
        return def.resolveAfterCreateCallbacks(activeTraits);
    }

    /**
     * Resolves transient-aware beforeCreate callbacks from definition and active traits.
     */
    private List<TransientAwareCallback> resolveTransientBeforeCreateCallbacks() {
        FactoryDefinitionRegistry registry = ((JootContextImpl) jootContext).getDefinitionRegistry();
        FactoryDefinition<R> def = registry.resolve(table);
        if (def == null) {
            return Collections.emptyList();
        }
        return def.resolveTransientBeforeCreateCallbacks(activeTraits);
    }

    /**
     * Resolves transient-aware afterCreate callbacks from definition and active traits.
     */
    private List<TransientAwareCallback> resolveTransientAfterCreateCallbacks() {
        FactoryDefinitionRegistry registry = ((JootContextImpl) jootContext).getDefinitionRegistry();
        FactoryDefinition<R> def = registry.resolve(table);
        if (def == null) {
            return Collections.emptyList();
        }
        return def.resolveTransientAfterCreateCallbacks(activeTraits);
    }
}


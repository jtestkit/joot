package io.github.jtestkit.joot;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.TableField;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Default implementation of JootContext.
 * 
 * @since 0.1.0
 */
class JootContextImpl implements JootContext {
    
    private final DSLContext dsl;
    private final CyclicDependencyResolver cyclicResolver;
    private final GeneratorRegistry generatorRegistry;
    private final FactoryDefinitionRegistry definitionRegistry;
    private boolean generateNullablesGlobal = true;  // Default: true (production-like objects)
    
    /**
     * Creates a new JootContextImpl with the given DSLContext.
     * 
     * @param dsl the DSLContext to use
     */
    JootContextImpl(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext must not be null");
        this.cyclicResolver = new CyclicDependencyResolver(dsl);
        this.generatorRegistry = new GeneratorRegistry();
        this.definitionRegistry = new FactoryDefinitionRegistry();
    }
    
    @Override
    public JootContext generateNullables(boolean generate) {
        this.generateNullablesGlobal = generate;
        return this;
    }
    
    /**
     * Package-private getter for generateNullables flag.
     * Used by builders for recursive entity creation.
     */
    boolean getGenerateNullablesGlobal() {
        return generateNullablesGlobal;
    }
    
    /**
     * Package-private getter for cyclic resolver.
     * Used by builders for cyclic dependency resolution.
     */
    CyclicDependencyResolver getCyclicResolver() {
        return cyclicResolver;
    }
    
    /**
     * Package-private getter for generator registry.
     * Used by builders for value generation.
     */
    GeneratorRegistry getGeneratorRegistry() {
        return generatorRegistry;
    }

    /**
     * Package-private getter for definition registry.
     * Used by builders for resolving factory definitions.
     */
    FactoryDefinitionRegistry getDefinitionRegistry() {
        return definitionRegistry;
    }
    
    @Override
    public <P> PojoBuilder<P> create(Table<?> table, Class<P> pojoClass) {
        return new PojoBuilderImpl<>(dsl, table, pojoClass, 
                                      this, CreationChain.empty(), generateNullablesGlobal);
    }
    
    @Override
    public <R extends Record> RecordBuilder<R> createRecord(Table<R> table) {
        return new RecordBuilderImpl<>(dsl, table, 
                                       this, CreationChain.empty(), generateNullablesGlobal);
    }
    
    @Override
    public DSLContext dsl() {
        return dsl;
    }
    
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <P> P get(Object primaryKey, Table<?> table, Class<P> pojoClass) {
        TableField pk = getPrimaryKey(table);
        return (P) dsl.selectFrom((Table) table)
            .where(((Field) pk).eq(primaryKey))
            .fetchOneInto(pojoClass);
    }
    
    /**
     * Gets the primary key field of a table.
     * 
     * @throws IllegalStateException if table has no primary key
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private TableField getPrimaryKey(Table table) {
        if (table.getPrimaryKey() == null || table.getPrimaryKey().getFields().isEmpty()) {
            throw new IllegalStateException("Table " + table.getName() + " has no primary key");
        }
        return (TableField) table.getPrimaryKey().getFields().get(0);
    }
    
    @Override
    public <T> JootContext registerGenerator(Field<T> field, ValueGenerator<T> generator) {
        generatorRegistry.registerFieldGenerator(field, generator);
        return this;
    }
    
    @Override
    public <T> JootContext registerGenerator(Class<T> type, ValueGenerator<T> generator) {
        generatorRegistry.registerTypeGenerator(type, generator);
        return this;
    }

    @Override
    public <T> JootContext sequence(Field<T> field, LongFunction<T> sequenceFn) {
        AtomicLong counter = new AtomicLong(1);
        registerGenerator(field, (maxLen, isUnique) -> sequenceFn.apply(counter.getAndIncrement()));
        return this;
    }

    @Override
    public <R extends Record> JootContext define(Table<R> table, Consumer<FactoryDefinitionBuilder<R>> config) {
        FactoryDefinitionBuilder<R> builder = new FactoryDefinitionBuilder<>();
        config.accept(builder);
        definitionRegistry.register(table, builder.build(table));
        return this;
    }

    @Override
    public <R extends Record> JootContext define(String name, Table<R> table, Consumer<FactoryDefinitionBuilder<R>> config) {
        FactoryDefinitionBuilder<R> builder = new FactoryDefinitionBuilder<>();
        config.accept(builder);
        FactoryDefinition<R> def = builder.build(table);
        definitionRegistry.register(name, def);
        // Also register as the table's default definition so create(TABLE) picks it up
        definitionRegistry.register(table, def);
        return this;
    }
}


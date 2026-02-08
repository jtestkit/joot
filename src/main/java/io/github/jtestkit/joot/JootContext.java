                                                                                                                                                                                                                                                                                                                                                                                            package io.github.jtestkit.joot;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Main context for Joot test framework.
 * Provides access to DSLContext and methods for creating test entities.
 * 
 * @since 0.1.0
 */
public interface JootContext {
    
    /**
     * Creates a new JootContext with the given DSLContext.
     * 
     * @param dsl the DSLContext to use for database operations
     * @return a new JootContext instance
     * @throws NullPointerException if dsl is null
     */
    static JootContext create(DSLContext dsl) {
        Objects.requireNonNull(dsl, "DSLContext must not be null");
        return new JootContextImpl(dsl);                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            
    }
    
    /**
     * Sets the global generateNullables flag for this context.
     * By default, generateNullables is TRUE (production-like objects with all fields populated).
     * Set to FALSE for minimalist mode (only NOT NULL fields).
     * 
     * @param generate true to generate nullable fields (default), false to skip them
     * @return this context for chaining
     */
    JootContext generateNullables(boolean generate);
    
    /**
     * Creates a builder for the given table and POJO class.
     * 
     * @param table the jOOQ table
     * @param pojoClass the POJO class to create
     * @param <P> the POJO type
     * @return a PojoBuilder for creating the entity
     */
    <P> PojoBuilder<P> create(Table<?> table, Class<P> pojoClass);
    
    /**
     * Creates a builder for the given table that returns a jOOQ Record.
     * This is an alternative to {@link #create(Table, Class)} when working with Records is preferred.
     * 
     * @param table the jOOQ table
     * @param <R> the Record type
     * @return a RecordBuilder for creating the entity
     */
    <R extends Record> RecordBuilder<R> createRecord(Table<R> table);
    
    /**
     * Returns the underlying DSLContext for direct database access.
     * 
     * @return the DSLContext instance
     */
    DSLContext dsl();
    
    /**
     * Retrieves an entity by its primary key.
     * Executes a SELECT query to fetch the entity from the database.
     *
     * @param primaryKey the primary key value
     * @param table the jOOQ table
     * @param pojoClass the POJO class to map to
     * @param <P> the POJO type
     * @return the entity, or null if not found
     */
    <P> P get(Object primaryKey, Table<?> table, Class<P> pojoClass);
    
    /**
     * Registers a custom value generator for a specific field.
     * This takes precedence over type-based generators.
     * 
     * @param field the field to generate values for
     * @param generator the generator to use
     * @param <T> the field type
     * @return this context for method chaining
     */
    <T> JootContext registerGenerator(org.jooq.Field<T> field, ValueGenerator<T> generator);
    
    /**
     * Registers a custom value generator for a specific type.
     * This overrides built-in generators for the given type.
     * 
     * @param type the type to generate values for
     * @param generator the generator to use
     * @param <T> the value type
     * @return this context for method chaining
     */
    <T> JootContext registerGenerator(Class<T> type, ValueGenerator<T> generator);

    /**
     * Defines a factory for the given table with default values, traits, and callbacks.
     * Definitions are optional — auto-generation works without them.
     *
     * @param table the jOOQ table to define a factory for
     * @param config a consumer that configures the factory definition
     * @param <R> the Record type
     * @return this context for chaining
     */
    <R extends Record> JootContext define(Table<R> table, Consumer<FactoryDefinitionBuilder<R>> config);

    /**
     * Defines a named factory that can inherit from another definition.
     * Use with {@code f.parent("parentName")} for inheritance.
     *
     * @param name unique name for this factory definition
     * @param table the jOOQ table
     * @param config a consumer that configures the factory definition
     * @param <R> the Record type
     * @return this context for chaining
     */
    <R extends Record> JootContext define(String name, Table<R> table, Consumer<FactoryDefinitionBuilder<R>> config);

    /**
     * Registers a sequence generator for a field.
     * The function receives an incrementing long value starting from 1.
     *
     * @param field the field to generate sequence values for
     * @param sequenceFn function that converts sequence number to field value
     * @param <T> the field type
     * @return this context for chaining
     */
    <T> JootContext sequence(org.jooq.Field<T> field, java.util.function.LongFunction<T> sequenceFn);
}


package io.github.jtestkit.joot;

import org.jooq.Field;
import org.jooq.Record;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Fluent builder for creating jOOQ Record entities.
 * Alternative to PojoBuilder for cases when working directly with jOOQ Records is preferred.
 * 
 * <p>Example usage:
 * <pre>{@code
 * AuthorRecord author = ctx.createRecord(AUTHOR)
 *     .set(AUTHOR.NAME, "Isaac Asimov")
 *     .build();
 * }</pre>
 * 
 * @param <R> the Record type
 * @since 0.1.0
 */
public interface RecordBuilder<R extends Record> {
    
    /**
     * Sets an explicit value for a field.
     * 
     * @param field the field to set
     * @param value the value to set
     * @param <T> the field type
     * @return this builder for chaining
     */
    <T> RecordBuilder<R> set(Field<T> field, T value);
    
    /**
     * Controls generation of nullable fields for this specific build.
     * Overrides the global context setting.
     * By default (true), nullable fields are generated for production-like objects.
     * Set to false for minimalist mode (only NOT NULL fields).
     * 
     * @param generate true to generate nullable fields, false to skip them
     * @return this builder for chaining
     */
    RecordBuilder<R> generateNullables(boolean generate);
    
    /**
     * Registers a custom value generator for a specific field in this builder only.
     * This takes precedence over global field-specific generators and type-based generators,
     * but not over explicit values set via {@link #set(Field, Object)}.
     * <p>
     * Use this for per-entity custom generation logic (e.g., negative tests, edge cases).
     * 
     * @param field the field to generate values for
     * @param generator the generator to use
     * @param <T> the field type
     * @return this builder for chaining
     */
    <T> RecordBuilder<R> withGenerator(Field<T> field, ValueGenerator<T> generator);
    
    /**
     * Activates a named trait from the factory definition.
     * Multiple traits can be activated and they compose in order.
     *
     * @param traitName the name of the trait to activate
     * @return this builder for chaining
     * @throws IllegalArgumentException if no definition exists for this table or trait name is unknown
     */
    RecordBuilder<R> trait(String traitName);

    /**
     * Creates multiple entities, returning them as a list.
     *
     * @param count the number of entities to create
     * @return list of created records
     */
    List<R> times(int count);

    /**
     * Creates multiple entities with per-item customization.
     *
     * @param count the number of entities to create
     * @param customizer receives the builder and the 0-based index for each entity
     * @return list of created records
     */
    List<R> times(int count, BiConsumer<RecordBuilder<R>, Integer> customizer);

    /**
     * Sets a transient attribute that is passed to lifecycle callbacks but not persisted.
     *
     * @param name the attribute name
     * @param value the attribute value
     * @return this builder for chaining
     */
    RecordBuilder<R> transientAttr(String name, Object value);

    /**
     * Creates the entity in the database and returns the Record.
     *
     * @return the created Record with all fields populated (including generated PKs)
     */
    R build();

    /**
     * Builds a Record with all values populated but does NOT insert into the database.
     * Foreign key dependencies are NOT auto-created.
     * Lifecycle callbacks are NOT executed.
     *
     * @return the populated Record (not persisted)
     */
    R buildWithoutInsert();

    /**
     * Returns the resolved field-value map without creating a Record or inserting.
     * Useful for inspecting what values would be generated.
     *
     * @return map of fields to their resolved values
     */
    Map<Field<?>, Object> buildAttributes();
}


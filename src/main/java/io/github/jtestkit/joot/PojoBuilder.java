package io.github.jtestkit.joot;

import org.jooq.Field;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Builder for creating test entities as POJOs.
 * Provides fluent API for setting field values and building entities.
 * 
 * @param <P> the POJO type
 * @since 0.1.0
 */
public interface PojoBuilder<P> {
    
    /**
     * Sets a specific value for a field.
     * 
     * @param field the field to set
     * @param value the value to set
     * @param <T> the field type
     * @return this builder for chaining
     */
    <T> PojoBuilder<P> set(Field<T> field, T value);
    
    /**
     * Controls generation of nullable fields for this specific build.
     * Overrides the global context setting.
     * By default (true), nullable fields are generated for production-like objects.
     * Set to false for minimalist mode (only NOT NULL fields).
     * 
     * @param generate true to generate nullable fields, false to skip them
     * @return this builder for chaining
     */
    PojoBuilder<P> generateNullables(boolean generate);
    
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
    <T> PojoBuilder<P> withGenerator(Field<T> field, ValueGenerator<T> generator);
    
    /**
     * Activates a named trait from the factory definition.
     * Multiple traits can be activated and they compose in order.
     *
     * @param traitName the name of the trait to activate
     * @return this builder for chaining
     * @throws IllegalArgumentException if no definition exists for this table or trait name is unknown
     */
    PojoBuilder<P> trait(String traitName);

    /**
     * Creates multiple entities, returning them as a list.
     *
     * @param count the number of entities to create
     * @return list of created POJOs
     */
    List<P> times(int count);

    /**
     * Creates multiple entities with per-item customization.
     *
     * @param count the number of entities to create
     * @param customizer receives the builder and the 0-based index for each entity
     * @return list of created POJOs
     */
    List<P> times(int count, BiConsumer<PojoBuilder<P>, Integer> customizer);

    /**
     * Sets a transient attribute that is passed to lifecycle callbacks but not persisted.
     *
     * @param name the attribute name
     * @param value the attribute value
     * @return this builder for chaining
     */
    PojoBuilder<P> transientAttr(String name, Object value);

    /**
     * Builds the entity, inserts it into the database, and returns the POJO.
     *
     * @return the created POJO with all values including generated ID
     */
    P build();

    /**
     * Builds a POJO with all values populated but does NOT insert into the database.
     * Foreign key dependencies are NOT auto-created.
     * Lifecycle callbacks are NOT executed.
     *
     * @return the populated POJO (not persisted)
     */
    P buildWithoutInsert();

    /**
     * Returns the resolved field-value map without creating an entity or inserting.
     * Useful for inspecting what values would be generated.
     *
     * @return map of fields to their resolved values
     */
    Map<Field<?>, Object> buildAttributes();
}


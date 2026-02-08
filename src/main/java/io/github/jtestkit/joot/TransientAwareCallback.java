package io.github.jtestkit.joot;

import org.jooq.Record;

/**
 * Lifecycle callback that receives both the record and transient attributes.
 * Use this when your callback needs access to transient (non-persisted) values.
 *
 * <p>Example:
 * <pre>{@code
 * ctx.define(AUTHOR, f -> {
 *     f.afterCreate((record, transients) -> {
 *         int bookCount = transients.getOrDefault("bookCount", Integer.class, 0);
 *         for (int i = 0; i < bookCount; i++) {
 *             ctx.createRecord(BOOK).set(BOOK.AUTHOR_ID, record.get(AUTHOR.ID)).build();
 *         }
 *     });
 * });
 * }</pre>
 *
 * @since 0.10.0
 */
@FunctionalInterface
public interface TransientAwareCallback {

    /**
     * Called with the created record and transient attributes.
     *
     * @param record the record (before or after INSERT depending on context)
     * @param transients the transient attributes set on the builder
     */
    void accept(Record record, TransientAttributes transients);
}

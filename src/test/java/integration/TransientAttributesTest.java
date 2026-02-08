package integration;

import io.github.jtestkit.joot.JootContext;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Author;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.jtestkit.joot.test.fixtures.Tables.AUTHOR;
import static io.github.jtestkit.joot.test.fixtures.Tables.BOOK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for transient attributes — non-persisted values passed to callbacks.
 */
class TransientAttributesTest extends BaseIntegrationTest {

    private JootContext ctx;

    @BeforeEach
    void setupContext() {
        ctx = JootContext.create(dsl);
    }

    @Test
    void shouldPassTransientToAfterCreateCallback() {
        List<Integer> capturedValues = new ArrayList<>();

        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate((record, transients) -> {
                int bookCount = transients.getOrDefault("bookCount", Integer.class, 0);
                capturedValues.add(bookCount);
            });
        });

        ctx.create(AUTHOR, Author.class)
                .transientAttr("bookCount", 3)
                .build();

        assertThat(capturedValues).containsExactly(3);
    }

    @Test
    void shouldTransientCreateChildEntities() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Prolific");
            f.afterCreate((record, transients) -> {
                int bookCount = transients.getOrDefault("bookCount", Integer.class, 0);
                UUID authorId = (UUID) record.get(AUTHOR.ID);
                for (int i = 0; i < bookCount; i++) {
                    ctx.create(BOOK, Book.class).set(BOOK.AUTHOR_ID, authorId).build();
                }
            });
        });

        Author author = ctx.create(AUTHOR, Author.class)
                .transientAttr("bookCount", 3)
                .build();

        int bookCount = dsl.selectCount().from(BOOK)
                .where(BOOK.AUTHOR_ID.eq(author.getId()))
                .fetchOne(0, int.class);
        assertThat(bookCount).isEqualTo(3);
    }

    @Test
    void shouldTransientDefaultToNullWhenNotSet() {
        List<String> capturedValues = new ArrayList<>();

        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate((record, transients) -> {
                String val = transients.get("missing", String.class);
                capturedValues.add(val == null ? "null" : val);
            });
        });

        ctx.create(AUTHOR, Author.class).build();

        assertThat(capturedValues).containsExactly("null");
    }

    @Test
    void shouldTransientWorkWithTraitCallbacks() {
        List<String> log = new ArrayList<>();

        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.trait("verbose", t -> t.afterCreate((record, transients) -> {
                String tag = transients.getOrDefault("tag", String.class, "default");
                log.add("trait:" + tag);
            }));
        });

        ctx.create(AUTHOR, Author.class)
                .trait("verbose")
                .transientAttr("tag", "custom")
                .build();

        assertThat(log).containsExactly("trait:custom");
    }

    @Test
    void shouldTransientPassToBeforeCreateCallback() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Placeholder");
            f.beforeCreate((record, transients) -> {
                String name = transients.getOrDefault("name", String.class, "Fallback");
                record.set(AUTHOR.NAME, name);
            });
        });

        Author author = ctx.create(AUTHOR, Author.class)
                .transientAttr("name", "Dynamic Name")
                .build();

        assertThat(author.getName()).isEqualTo("Dynamic Name");
    }

    @Test
    void shouldTransientWorkWithRecordBuilder() {
        List<Integer> captured = new ArrayList<>();

        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate((record, transients) -> {
                captured.add(transients.getOrDefault("priority", Integer.class, 0));
            });
        });

        ctx.createRecord(AUTHOR)
                .transientAttr("priority", 42)
                .build();

        assertThat(captured).containsExactly(42);
    }

    @Test
    void shouldTransientWorkWithInheritedCallbacks() {
        List<String> log = new ArrayList<>();

        ctx.define("baseAuthor", AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate((record, transients) -> {
                log.add("parent:" + transients.getOrDefault("tag", String.class, "none"));
            });
        });

        ctx.define(AUTHOR, f -> {
            f.parent("baseAuthor");
            f.afterCreate((record, transients) -> {
                log.add("child:" + transients.getOrDefault("tag", String.class, "none"));
            });
        });

        ctx.create(AUTHOR, Author.class)
                .transientAttr("tag", "hello")
                .build();

        assertThat(log).containsExactly("parent:hello", "child:hello");
    }

    @Test
    void shouldTransientWorkWithTimes() {
        List<Integer> captured = new ArrayList<>();

        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate((record, transients) -> {
                captured.add(transients.getOrDefault("n", Integer.class, 0));
            });
        });

        ctx.create(AUTHOR, Author.class)
                .transientAttr("n", 7)
                .times(3);

        assertThat(captured).containsExactly(7, 7, 7);
    }
}

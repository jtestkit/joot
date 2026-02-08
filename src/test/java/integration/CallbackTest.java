package integration;

import io.github.jtestkit.joot.JootContext;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Author;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.jtestkit.joot.test.fixtures.Tables.AUTHOR;
import static io.github.jtestkit.joot.test.fixtures.Tables.BOOK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for beforeCreate/afterCreate lifecycle callbacks.
 */
class CallbackTest extends BaseIntegrationTest {

    private JootContext ctx;

    @BeforeEach
    void setupContext() {
        ctx = JootContext.create(dsl);
    }

    @Test
    void shouldExecuteBeforeCreateCallback() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Original");
            f.beforeCreate(record -> record.set(AUTHOR.NAME, "Modified By Callback"));
        });

        Author author = ctx.create(AUTHOR, Author.class).build();

        assertThat(author.getName()).isEqualTo("Modified By Callback");
    }

    @Test
    void shouldExecuteAfterCreateCallback() {
        List<Object> createdIds = new ArrayList<>();

        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate(record -> createdIds.add(record.get(AUTHOR.ID)));
        });

        Author author = ctx.create(AUTHOR, Author.class).build();

        assertThat(createdIds).hasSize(1);
        assertThat(createdIds.get(0)).isEqualTo(author.getId());
    }

    @Test
    void shouldAfterCreateCallbackCreateChildEntities() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Prolific Author");
            f.afterCreate(record -> {
                Object authorId = record.get(AUTHOR.ID);
                ctx.create(BOOK, Book.class).set(BOOK.AUTHOR_ID, (java.util.UUID) authorId).build();
                ctx.create(BOOK, Book.class).set(BOOK.AUTHOR_ID, (java.util.UUID) authorId).build();
            });
        });

        Author author = ctx.create(AUTHOR, Author.class).build();

        // Verify 2 books were created by callback
        int bookCount = dsl.selectCount().from(BOOK)
                .where(BOOK.AUTHOR_ID.eq(author.getId()))
                .fetchOne(0, int.class);
        assertThat(bookCount).isEqualTo(2);
    }

    @Test
    void shouldTraitCallbackComposeWithBaseCallback() {
        List<String> callbackLog = new ArrayList<>();

        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate(record -> callbackLog.add("base"));
            f.trait("logged", t -> t.afterCreate(record -> callbackLog.add("trait")));
        });

        ctx.create(AUTHOR, Author.class).trait("logged").build();

        assertThat(callbackLog).containsExactly("base", "trait");
    }

    @Test
    void shouldCallbackNotExecuteWithoutDefine() {
        // No define — no callbacks — should just work normally
        Author author = ctx.create(AUTHOR, Author.class).build();
        assertThat(author).isNotNull();
    }
}

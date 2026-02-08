package integration;

import io.github.jtestkit.joot.JootContext;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Author;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.jtestkit.joot.test.fixtures.Tables.AUTHOR;
import static io.github.jtestkit.joot.test.fixtures.Tables.BOOK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ctx.sequence() — named sequences for predictable unique values.
 */
class SequenceGeneratorTest extends BaseIntegrationTest {

    private JootContext ctx;

    @BeforeEach
    void setupContext() {
        ctx = JootContext.create(dsl);
    }

    @Test
    void shouldGenerateSequentialValues() {
        ctx.sequence(AUTHOR.EMAIL, n -> "author" + n + "@test.com");

        Author a1 = ctx.create(AUTHOR, Author.class).build();
        Author a2 = ctx.create(AUTHOR, Author.class).build();
        Author a3 = ctx.create(AUTHOR, Author.class).build();

        assertThat(a1.getEmail()).isEqualTo("author1@test.com");
        assertThat(a2.getEmail()).isEqualTo("author2@test.com");
        assertThat(a3.getEmail()).isEqualTo("author3@test.com");
    }

    @Test
    void shouldSequenceBeOverriddenByExplicitSet() {
        ctx.sequence(AUTHOR.EMAIL, n -> "seq" + n + "@test.com");

        Author author = ctx.create(AUTHOR, Author.class)
                .set(AUTHOR.EMAIL, "custom@test.com")
                .build();

        assertThat(author.getEmail()).isEqualTo("custom@test.com");
    }

    @Test
    void shouldSequenceWorkWithDefine() {
        ctx.sequence(AUTHOR.EMAIL, n -> "seq" + n + "@test.com");
        ctx.define(AUTHOR, f -> f.set(AUTHOR.NAME, "Defined Author"));

        Author author = ctx.create(AUTHOR, Author.class).build();

        assertThat(author.getName()).isEqualTo("Defined Author");
        assertThat(author.getEmail()).startsWith("seq");
    }

    @Test
    void shouldSequenceWorkWithIntegerFields() {
        ctx.sequence(BOOK.PAGES, n -> (int) (n * 100));

        ctx.define(BOOK, f -> f.set(BOOK.TITLE, "Test Book"));

        Book b1 = ctx.create(BOOK, Book.class).build();
        Book b2 = ctx.create(BOOK, Book.class).build();

        assertThat(b1.getPages()).isEqualTo(100);
        assertThat(b2.getPages()).isEqualTo(200);
    }
}

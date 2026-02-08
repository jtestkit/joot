package integration;

import io.github.jtestkit.joot.JootContext;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Author;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Book;
import io.github.jtestkit.joot.test.fixtures.tables.records.AuthorRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.jtestkit.joot.test.fixtures.Tables.AUTHOR;
import static io.github.jtestkit.joot.test.fixtures.Tables.BOOK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for .times() batch creation.
 */
class BatchCreationTest extends BaseIntegrationTest {

    private JootContext ctx;

    @BeforeEach
    void setupContext() {
        ctx = JootContext.create(dsl);
    }

    @Test
    void shouldCreateMultipleEntities() {
        List<Author> authors = ctx.create(AUTHOR, Author.class).times(5);

        assertThat(authors).hasSize(5);
        // All should have unique IDs
        Set<Object> ids = authors.stream().map(Author::getId).collect(Collectors.toSet());
        assertThat(ids).hasSize(5);
    }

    @Test
    void shouldTimesWorkWithRecordBuilder() {
        List<AuthorRecord> records = ctx.createRecord(AUTHOR).times(3);

        assertThat(records).hasSize(3);
        records.forEach(r -> assertThat(r.getId()).isNotNull());
    }

    @Test
    void shouldTimesWorkWithCustomizer() {
        List<Author> authors = ctx.create(AUTHOR, Author.class)
                .times(3, (builder, i) -> builder.set(AUTHOR.NAME, "Author " + i));

        assertThat(authors).hasSize(3);
        assertThat(authors.get(0).getName()).isEqualTo("Author 0");
        assertThat(authors.get(1).getName()).isEqualTo("Author 1");
        assertThat(authors.get(2).getName()).isEqualTo("Author 2");
    }

    @Test
    void shouldTimesWorkWithDefine() {
        ctx.define(AUTHOR, f -> f.set(AUTHOR.COUNTRY, "US"));

        List<Author> authors = ctx.create(AUTHOR, Author.class).times(3);

        assertThat(authors).hasSize(3);
        authors.forEach(a -> assertThat(a.getCountry()).isEqualTo("US"));
    }

    @Test
    void shouldTimesWorkWithTrait() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.COUNTRY, "US");
            f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
        });

        List<Author> authors = ctx.create(AUTHOR, Author.class)
                .trait("european")
                .times(3);

        assertThat(authors).hasSize(3);
        authors.forEach(a -> assertThat(a.getCountry()).isEqualTo("DE"));
    }

    @Test
    void shouldTimesWorkWithFKAutoCreation() {
        List<Book> books = ctx.create(BOOK, Book.class).times(3);

        assertThat(books).hasSize(3);
        books.forEach(b -> assertThat(b.getAuthorId()).isNotNull());
    }

    @Test
    void shouldTimesWithOneReturnSingleElementList() {
        List<Author> authors = ctx.create(AUTHOR, Author.class).times(1);
        assertThat(authors).hasSize(1);
    }

    @Test
    void shouldTimesZeroReturnEmptyList() {
        List<Author> authors = ctx.create(AUTHOR, Author.class).times(0);
        assertThat(authors).isEmpty();
    }
}

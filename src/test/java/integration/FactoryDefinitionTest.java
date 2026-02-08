package integration;

import io.github.jtestkit.joot.JootContext;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Author;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Book;
import io.github.jtestkit.joot.test.fixtures.tables.records.AuthorRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.jtestkit.joot.test.fixtures.Tables.AUTHOR;
import static io.github.jtestkit.joot.test.fixtures.Tables.BOOK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for factory definitions: define defaults, override, and use with/without definitions.
 */
class FactoryDefinitionTest extends BaseIntegrationTest {

    private JootContext ctx;

    @BeforeEach
    void setupContext() {
        ctx = JootContext.create(dsl);
    }

    @Test
    void shouldUseDefinitionDefaults() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Isaac Asimov");
            f.set(AUTHOR.COUNTRY, "US");
        });

        Author author = ctx.create(AUTHOR, Author.class).build();

        assertThat(author.getName()).isEqualTo("Isaac Asimov");
        assertThat(author.getCountry()).isEqualTo("US");
    }

    @Test
    void shouldOverrideDefinitionWithExplicitSet() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Isaac Asimov");
            f.set(AUTHOR.COUNTRY, "US");
        });

        Author author = ctx.create(AUTHOR, Author.class)
                .set(AUTHOR.NAME, "Arthur Clarke")
                .build();

        assertThat(author.getName()).isEqualTo("Arthur Clarke");
        assertThat(author.getCountry()).isEqualTo("US"); // from definition
    }

    @Test
    void shouldWorkWithoutDefinition() {
        // No define() call — auto-generation should still work
        Author author = ctx.create(AUTHOR, Author.class).build();

        assertThat(author).isNotNull();
        assertThat(author.getId()).isNotNull();
        assertThat(author.getName()).isNotNull();
    }

    @Test
    void shouldUseDefinitionWithRecordBuilder() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Philip K. Dick");
            f.set(AUTHOR.COUNTRY, "US");
        });

        AuthorRecord record = ctx.createRecord(AUTHOR).build();

        assertThat(record.getName()).isEqualTo("Philip K. Dick");
        assertThat(record.getCountry()).isEqualTo("US");
    }

    @Test
    void shouldAutoCreateFKWithDefinition() {
        ctx.define(BOOK, f -> {
            f.set(BOOK.TITLE, "Foundation");
            f.set(BOOK.PAGES, 255);
        });

        Book book = ctx.create(BOOK, Book.class).build();

        assertThat(book.getTitle()).isEqualTo("Foundation");
        assertThat(book.getPages()).isEqualTo(255);
        assertThat(book.getAuthorId()).isNotNull(); // auto-created FK
    }

    @Test
    void shouldUseDefinitionGenerators() {
        ctx.define(AUTHOR, f -> {
            f.withGenerator(AUTHOR.NAME, (maxLen, isUnique) -> "Generated Author");
        });

        Author a1 = ctx.create(AUTHOR, Author.class).build();
        Author a2 = ctx.create(AUTHOR, Author.class).build();

        assertThat(a1.getName()).isEqualTo("Generated Author");
        assertThat(a2.getName()).isEqualTo("Generated Author");
    }
}

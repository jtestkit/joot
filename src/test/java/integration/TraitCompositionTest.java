package integration;

import io.github.jtestkit.joot.JootContext;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Author;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.jtestkit.joot.test.fixtures.Tables.AUTHOR;
import static io.github.jtestkit.joot.test.fixtures.Tables.BOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for trait composition: single trait, multiple traits, override order, trait + explicit set.
 */
class TraitCompositionTest extends BaseIntegrationTest {

    private JootContext ctx;

    @BeforeEach
    void setupContext() {
        ctx = JootContext.create(dsl);
    }

    @Test
    void shouldApplySingleTrait() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Default Author");
            f.set(AUTHOR.COUNTRY, "US");
            f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
        });

        Author author = ctx.create(AUTHOR, Author.class)
                .trait("european")
                .build();

        assertThat(author.getName()).isEqualTo("Default Author");
        assertThat(author.getCountry()).isEqualTo("DE");
    }

    @Test
    void shouldComposeMultipleTraits() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Default Author");
            f.set(AUTHOR.COUNTRY, "US");
            f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
            f.trait("renamed", t -> t.set(AUTHOR.NAME, "Renamed Author"));
        });

        Author author = ctx.create(AUTHOR, Author.class)
                .trait("european")
                .trait("renamed")
                .build();

        assertThat(author.getCountry()).isEqualTo("DE");
        assertThat(author.getName()).isEqualTo("Renamed Author");
    }

    @Test
    void shouldRespectTraitOrder_lastTraitWins() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.COUNTRY, "US");
            f.trait("german", t -> t.set(AUTHOR.COUNTRY, "DE"));
            f.trait("french", t -> t.set(AUTHOR.COUNTRY, "FR"));
        });

        Author author = ctx.create(AUTHOR, Author.class)
                .trait("german")
                .trait("french")
                .build();

        assertThat(author.getCountry()).isEqualTo("FR"); // last trait wins
    }

    @Test
    void shouldExplicitSetOverrideTrait() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.COUNTRY, "US");
            f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
        });

        Author author = ctx.create(AUTHOR, Author.class)
                .trait("european")
                .set(AUTHOR.COUNTRY, "JP")
                .build();

        assertThat(author.getCountry()).isEqualTo("JP"); // explicit set wins over trait
    }

    @Test
    void shouldTraitWorkWithRecordBuilder() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Default");
            f.trait("special", t -> t.set(AUTHOR.NAME, "Special Author"));
        });

        var record = ctx.createRecord(AUTHOR)
                .trait("special")
                .build();

        assertThat(record.getName()).isEqualTo("Special Author");
    }

    @Test
    void shouldTraitWorkWithFKAutoCreation() {
        ctx.define(BOOK, f -> {
            f.set(BOOK.TITLE, "Default Title");
            f.trait("long_book", t -> t.set(BOOK.PAGES, 1000));
        });

        Book book = ctx.create(BOOK, Book.class)
                .trait("long_book")
                .build();

        assertThat(book.getTitle()).isEqualTo("Default Title");
        assertThat(book.getPages()).isEqualTo(1000);
        assertThat(book.getAuthorId()).isNotNull(); // FK auto-created
    }

    @Test
    void shouldBaseDefinitionWorkWithoutTraits() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Base Author");
            f.trait("unused", t -> t.set(AUTHOR.NAME, "Unused"));
        });

        Author author = ctx.create(AUTHOR, Author.class).build();

        assertThat(author.getName()).isEqualTo("Base Author"); // no trait applied
    }

    @Test
    void shouldThrowOnUnknownTraitName() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
        });

        assertThatThrownBy(() -> ctx.create(AUTHOR, Author.class).trait("typo").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown trait 'typo'");
    }

    @Test
    void shouldThrowOnTraitWithoutDefinition() {
        // No define() call — using trait should fail
        assertThatThrownBy(() -> ctx.create(AUTHOR, Author.class).trait("anything").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no factory definition");
    }
}

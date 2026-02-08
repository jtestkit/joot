package integration;

import io.github.jtestkit.joot.JootContext;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Author;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.jtestkit.joot.test.fixtures.Tables.AUTHOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for factory definition inheritance via parent().
 */
class FactoryInheritanceTest extends BaseIntegrationTest {

    private JootContext ctx;

    @BeforeEach
    void setupContext() {
        ctx = JootContext.create(dsl);
    }

    @Test
    void shouldInheritDefaultsFromParent() {
        ctx.define("baseAuthor", AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Base Author");
            f.set(AUTHOR.COUNTRY, "US");
        });

        ctx.define(AUTHOR, f -> {
            f.parent("baseAuthor");
            f.set(AUTHOR.NAME, "Child Author");
        });

        Author author = ctx.create(AUTHOR, Author.class).build();

        assertThat(author.getName()).isEqualTo("Child Author"); // overridden
        assertThat(author.getCountry()).isEqualTo("US"); // inherited from parent
    }

    @Test
    void shouldInheritCallbacksFromParent() {
        List<String> callbackLog = new ArrayList<>();

        ctx.define("baseAuthor", AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate(record -> callbackLog.add("parent"));
        });

        ctx.define(AUTHOR, f -> {
            f.parent("baseAuthor");
            f.afterCreate(record -> callbackLog.add("child"));
        });

        ctx.create(AUTHOR, Author.class).build();

        assertThat(callbackLog).containsExactly("parent", "child");
    }

    @Test
    void shouldInheritTraitsFromParent() {
        ctx.define("baseAuthor", AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Base Author");
            f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
        });

        ctx.define(AUTHOR, f -> {
            f.parent("baseAuthor");
            f.set(AUTHOR.NAME, "Child Author");
        });

        Author author = ctx.create(AUTHOR, Author.class).trait("european").build();

        assertThat(author.getName()).isEqualTo("Child Author");
        assertThat(author.getCountry()).isEqualTo("DE"); // trait inherited from parent
    }

    @Test
    void shouldChildTraitOverrideParentTrait() {
        ctx.define("baseAuthor", AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.trait("regional", t -> t.set(AUTHOR.COUNTRY, "US"));
        });

        ctx.define(AUTHOR, f -> {
            f.parent("baseAuthor");
            f.trait("regional", t -> t.set(AUTHOR.COUNTRY, "JP"));
        });

        Author author = ctx.create(AUTHOR, Author.class).trait("regional").build();

        assertThat(author.getCountry()).isEqualTo("JP"); // child trait wins
    }

    @Test
    void shouldInheritGeneratorsFromParent() {
        ctx.define("baseAuthor", AUTHOR, f -> {
            f.withGenerator(AUTHOR.NAME, (maxLen, isUnique) -> "Generated Name");
        });

        ctx.define(AUTHOR, f -> {
            f.parent("baseAuthor");
            f.set(AUTHOR.COUNTRY, "FR");
        });

        Author author = ctx.create(AUTHOR, Author.class).build();

        assertThat(author.getName()).isEqualTo("Generated Name"); // inherited generator
        assertThat(author.getCountry()).isEqualTo("FR");
    }

    @Test
    void shouldThrowOnCyclicInheritance() {
        ctx.define("a", AUTHOR, f -> {
            f.parent("b");
            f.set(AUTHOR.NAME, "A");
        });

        ctx.define("b", AUTHOR, f -> {
            f.parent("a");
            f.set(AUTHOR.NAME, "B");
        });

        // Resolving "a" → parent "b" → parent "a" → cycle!
        assertThatThrownBy(() -> ctx.create(AUTHOR, Author.class).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Circular factory inheritance");
    }

    @Test
    void shouldThrowOnMissingParent() {
        ctx.define(AUTHOR, f -> {
            f.parent("nonExistent");
            f.set(AUTHOR.NAME, "Orphan");
        });

        assertThatThrownBy(() -> ctx.create(AUTHOR, Author.class).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }
}

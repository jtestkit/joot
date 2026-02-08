package integration;

import io.github.jtestkit.joot.JootContext;
import io.github.jtestkit.joot.test.fixtures.tables.pojos.Author;
import io.github.jtestkit.joot.test.fixtures.tables.records.AuthorRecord;
import org.jooq.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.jtestkit.joot.test.fixtures.Tables.AUTHOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for build strategies: buildWithoutInsert() and buildAttributes().
 */
class BuildStrategiesTest extends BaseIntegrationTest {

    private JootContext ctx;

    @BeforeEach
    void setupContext() {
        ctx = JootContext.create(dsl);
    }

    @Test
    void shouldBuildRecordWithoutInsert() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Not Persisted");
            f.set(AUTHOR.COUNTRY, "XX");
        });

        AuthorRecord record = ctx.createRecord(AUTHOR).buildWithoutInsert();

        assertThat(record.getName()).isEqualTo("Not Persisted");
        assertThat(record.getCountry()).isEqualTo("XX");

        // Verify NOT inserted into database
        int count = dsl.selectCount().from(AUTHOR).fetchOne(0, int.class);
        assertThat(count).isZero();
    }

    @Test
    void shouldBuildPojoWithoutInsert() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "POJO Not Persisted");
        });

        Author author = ctx.create(AUTHOR, Author.class).buildWithoutInsert();

        assertThat(author.getName()).isEqualTo("POJO Not Persisted");

        // Verify NOT inserted
        int count = dsl.selectCount().from(AUTHOR).fetchOne(0, int.class);
        assertThat(count).isZero();
    }

    @Test
    void shouldBuildWithoutInsertRespectExplicitSet() {
        AuthorRecord record = ctx.createRecord(AUTHOR)
                .set(AUTHOR.NAME, "Explicit")
                .set(AUTHOR.COUNTRY, "UK")
                .buildWithoutInsert();

        assertThat(record.getName()).isEqualTo("Explicit");
        assertThat(record.getCountry()).isEqualTo("UK");
    }

    @Test
    void shouldBuildAttributesReturnFieldValueMap() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Attr Author");
            f.set(AUTHOR.COUNTRY, "US");
        });

        Map<Field<?>, Object> attrs = ctx.createRecord(AUTHOR).buildAttributes();

        assertThat(attrs.get(AUTHOR.NAME)).isEqualTo("Attr Author");
        assertThat(attrs.get(AUTHOR.COUNTRY)).isEqualTo("US");

        // Verify NOT inserted
        int count = dsl.selectCount().from(AUTHOR).fetchOne(0, int.class);
        assertThat(count).isZero();
    }

    @Test
    void shouldBuildAttributesRespectTraits() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Default");
            f.set(AUTHOR.COUNTRY, "US");
            f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
        });

        Map<Field<?>, Object> attrs = ctx.createRecord(AUTHOR)
                .trait("european")
                .buildAttributes();

        assertThat(attrs.get(AUTHOR.NAME)).isEqualTo("Default");
        assertThat(attrs.get(AUTHOR.COUNTRY)).isEqualTo("DE");
    }

    @Test
    void shouldBuildAttributesFromPojoBuilder() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Pojo Attr");
        });

        Map<Field<?>, Object> attrs = ctx.create(AUTHOR, Author.class).buildAttributes();

        assertThat(attrs.get(AUTHOR.NAME)).isEqualTo("Pojo Attr");
    }

    @Test
    void shouldBuildWithoutInsertNotExecuteCallbacks() {
        // If callbacks were executed, they might try to insert and fail
        // or modify state — buildWithoutInsert should skip them
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Author");
            f.afterCreate(record -> {
                throw new RuntimeException("Callback should not execute");
            });
        });

        // Should NOT throw — callbacks are skipped
        AuthorRecord record = ctx.createRecord(AUTHOR).buildWithoutInsert();
        assertThat(record.getName()).isEqualTo("Author");
    }

    @Test
    void shouldBuildWithoutInsertRespectTraits() {
        ctx.define(AUTHOR, f -> {
            f.set(AUTHOR.NAME, "Default");
            f.set(AUTHOR.COUNTRY, "US");
            f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
        });

        AuthorRecord record = ctx.createRecord(AUTHOR)
                .trait("european")
                .buildWithoutInsert();

        assertThat(record.getName()).isEqualTo("Default");
        assertThat(record.getCountry()).isEqualTo("DE");
    }

    @Test
    void shouldBuildAttributesWithGenerators() {
        ctx.define(AUTHOR, f -> {
            f.withGenerator(AUTHOR.NAME, (maxLen, isUnique) -> "Gen Name");
            f.set(AUTHOR.COUNTRY, "US");
        });

        Map<Field<?>, Object> attrs = ctx.createRecord(AUTHOR).buildAttributes();

        assertThat(attrs.get(AUTHOR.NAME)).isEqualTo("Gen Name");
        assertThat(attrs.get(AUTHOR.COUNTRY)).isEqualTo("US");
    }
}

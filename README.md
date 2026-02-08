# Joot - jOOQ Test Data Factory

> Lightweight testing library for jOOQ applications with automatic foreign key resolution

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![jOOQ](https://img.shields.io/badge/jOOQ-3.13+-blue.svg)](https://www.jooq.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 🎯 Why Joot?

Testing with databases is hard. Setting up test data is tedious and error-prone:

```java
// ❌ Traditional approach: 30+ lines of boilerplate
AuthorRecord author = dsl.newRecord(AUTHOR);
author.setId(1L);
author.setName("Test Author");
author.setEmail("test@example.com");
author.setBio("Some bio");
author.setRating(4.5);
author.setCreatedAt(LocalDateTime.now());
// ... 10 more fields
author.insert();

BookRecord book = dsl.newRecord(BOOK);
book.setId(1L);
book.setAuthorId(author.getId());  // Manual FK!
book.setTitle("Test Book");
book.setIsbn("123-456");
// ... 15 more fields
book.insert();

// ✅ With Joot: 1 line, automatic FK resolution
Book book = ctx.create(BOOK, Book.class).build();
// author is auto-created, all NOT NULL fields populated, FK wired automatically ✨
```

**Joot gives you:**
- ✅ **Zero boilerplate** - focus on what matters in your tests
- ✅ **Automatic FK resolution** - no manual parent entity creation
- ✅ **Smart defaults** - generates realistic test data automatically
- ✅ **Type-safe API** - leverages jOOQ's generated code
- ✅ **Factory definitions & traits** - reusable templates with composable variations
- ✅ **Factory inheritance** - parent/child definitions with merging
- ✅ **Build strategies** - build without insert, inspect attributes
- ✅ **Transient attributes** - pass non-persisted data to callbacks
- ✅ **Production-ready** - 148 integration tests, 100% pass rate

---

## 🚀 Quick Start (5 minutes)

### 1. Add Dependency

**Gradle:**
```gradle
dependencies {
    testImplementation 'io.github.jtestkit:joot:0.9.0'
}
```

**Maven:**
```xml
<dependency>
    <groupId>io.github.jtestkit</groupId>
    <artifactId>joot</artifactId>
    <version>0.9.0</version>
    <scope>test</scope>
</dependency>
```

### 2. Setup in Test

```java
import io.github.jtestkit.joot.JootContext;
import org.jooq.DSLContext;

class MyTest {
    DSLContext dsl = ...; // Your jOOQ DSLContext
    JootContext ctx = JootContext.create(dsl);
    
    @Test
    void myTest() {
        // Create entities with zero boilerplate!
        Author author = ctx.create(AUTHOR, Author.class).build();
        
        // Data cleanup is your responsibility
        // (Use @Transactional, manual DELETE, or your preferred strategy)
    }
}
```

### 3. Start Testing!

```java
@Test
void shouldCreateBookWithAuthor() {
    // ACT: Create book (author auto-created!)
    Book book = ctx.create(BOOK, Book.class).build();
    
    // ASSERT
    assertThat(book.getAuthorId()).isNotNull();
    assertThat(book.getTitle()).isNotNull(); // All NOT NULL fields populated
    
    // Verify author was created
    Author author = ctx.get(book.getAuthorId(), AUTHOR, Author.class);
    assertThat(author).isNotNull();
}
```

---

## ✨ Key Features

### 1. Automatic Foreign Key Resolution

No need to manually create parent entities:

```java
// Creating a book automatically creates its author
Book book = ctx.create(BOOK, Book.class).build();

// Creating an order automatically creates user AND product
Order order = ctx.create(ORDER, Order.class).build();

// Even deep hierarchies work automatically
// Book -> Author -> Publisher -> Address -> Country ✨
```

### 2. Smart Default Values

All fields get sensible defaults based on type and constraints:

```java
Book book = ctx.create(BOOK, Book.class).build();

// NOT NULL fields are auto-populated:
book.getTitle()        // → "title_1"  (field name as prefix!)
book.getIsbn()         // → "isbn_1"
book.getAuthorId()     // → 1L (auto-created author)
book.getPrice()        // → random BigDecimal
book.getPublishedAt()  // → LocalDateTime.now()

// Enum fields get first value (deterministic)
order.getStatus()      // → OrderStatus.PENDING (always first from .values())

// UNIQUE fields get unique values automatically
book.getIsbn()         // → "isbn_1", "isbn_2", "isbn_3" (auto-incremented)
```

### 3. Explicit Values When Needed

Override defaults for fields that matter in your test:

```java
Book book = ctx.create(BOOK, Book.class)
    .set(BOOK.TITLE, "1984")
    .set(BOOK.PRICE, new BigDecimal("19.99"))
    .build();

// Only title and price are explicit, everything else auto-generated
```

### 4. Enum Support (Automatic!)

Enum fields are automatically handled - Joot uses the **first value** from `enum.values()` for deterministic behavior:

```java
public enum OrderStatus {
    PENDING,      // ← This one is used automatically
    CONFIRMED,
    SHIPPED,
    DELIVERED
}

// Automatic enum handling
Order order = ctx.create(ORDER, Order.class).build();
assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);  // ✅ Always first value

// Works with jOOQ EnumConverter
Task task = ctx.create(TASK, Task.class).build();
assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);  // ✅ First enum
```

**Why first value?**
- ✅ **Deterministic** - tests behave the same every time
- ✅ **Predictable** - you always know what to expect
- ✅ **Debuggable** - no random behavior

**Need a different value?**
```java
// Explicit value
Order shipped = ctx.create(ORDER, Order.class)
    .set(ORDER.STATUS, OrderStatus.SHIPPED)
    .build();

// Custom generator
ctx.registerGenerator(OrderStatus.class, (len, unique) -> OrderStatus.CONFIRMED);
```

### 5. Nullable Fields Control

```java
// By default, nullable fields are populated (production-like data)
Author author = ctx.create(AUTHOR, Author.class).build();
assertThat(author.getBio()).isNotNull();      // ✅ Generated
assertThat(author.getWebsite()).isNotNull();  // ✅ Generated

// Disable for minimal data
Author minimal = ctx.create(AUTHOR, Author.class)
    .generateNullables(false)
    .build();
assertThat(minimal.getBio()).isNull();      // ✅ NULL
assertThat(minimal.getName()).isNotNull();  // ✅ Still generated (NOT NULL)
```

### 6. Circular Dependency Handling

Joot intelligently handles circular references:

```java
// Self-reference: category.parent_id → category.id
Category root = ctx.create(CATEGORY, Category.class).build();
assertThat(root.getParentId()).isNotNull(); // Parent auto-created

// Circular FK: users ↔ team
Team team = ctx.create(TEAM, Team.class).build();
// Joot breaks the cycle automatically ✅
```

### 7. Data Access Helpers

```java
// Retrieve created entities by PK
Author author = ctx.get(1L, AUTHOR, Author.class);
```

### 8. Factory Definitions

Define reusable defaults for your entities:

```java
// Define once
ctx.define(AUTHOR, f -> {
    f.set(AUTHOR.NAME, "Isaac Asimov");
    f.set(AUTHOR.COUNTRY, "US");
});

// Use everywhere — defaults applied automatically
Author author = ctx.create(AUTHOR, Author.class).build();
assertThat(author.getName()).isEqualTo("Isaac Asimov");

// Override when needed
Author other = ctx.create(AUTHOR, Author.class)
    .set(AUTHOR.NAME, "Arthur Clarke")
    .build();
assertThat(other.getName()).isEqualTo("Arthur Clarke");
assertThat(other.getCountry()).isEqualTo("US"); // from definition
```

### 9. Traits

Named variations that compose on top of definitions:

```java
ctx.define(AUTHOR, f -> {
    f.set(AUTHOR.NAME, "Default Author");
    f.set(AUTHOR.COUNTRY, "US");

    f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
    f.trait("renamed", t -> t.set(AUTHOR.NAME, "Special Author"));
});

// Apply single trait
Author eu = ctx.create(AUTHOR, Author.class)
    .trait("european")
    .build();
assertThat(eu.getCountry()).isEqualTo("DE");

// Compose multiple traits (applied in order)
Author special = ctx.create(AUTHOR, Author.class)
    .trait("european")
    .trait("renamed")
    .build();
assertThat(special.getCountry()).isEqualTo("DE");
assertThat(special.getName()).isEqualTo("Special Author");

// Explicit .set() always wins over traits
Author jp = ctx.create(AUTHOR, Author.class)
    .trait("european")
    .set(AUTHOR.COUNTRY, "JP")
    .build();
assertThat(jp.getCountry()).isEqualTo("JP");
```

Unknown trait names throw `IllegalArgumentException` with a list of available traits.

### 10. Sequences

Predictable, auto-incrementing values for fields:

```java
ctx.sequence(AUTHOR.EMAIL, n -> "author" + n + "@test.com");

Author a1 = ctx.create(AUTHOR, Author.class).build();
Author a2 = ctx.create(AUTHOR, Author.class).build();
// a1.getEmail() == "author1@test.com"
// a2.getEmail() == "author2@test.com"

// Works with any type
ctx.sequence(BOOK.PAGES, n -> (int) (n * 100));
// 100, 200, 300, ...
```

### 11. Lifecycle Callbacks

Execute logic before/after entity insertion:

```java
ctx.define(AUTHOR, f -> {
    f.set(AUTHOR.NAME, "Author");

    f.beforeCreate(record -> {
        // Modify record before INSERT
        record.set(AUTHOR.NAME, record.get(AUTHOR.NAME).toUpperCase());
    });

    f.afterCreate(record -> {
        // Create related entities after INSERT
        Object authorId = record.get(AUTHOR.ID);
        ctx.create(BOOK, Book.class)
            .set(BOOK.AUTHOR_ID, (UUID) authorId)
            .build();
    });
});
```

Trait callbacks compose with base callbacks (base runs first, then trait):

```java
ctx.define(AUTHOR, f -> {
    f.afterCreate(r -> log("base"));
    f.trait("logged", t -> t.afterCreate(r -> log("trait")));
});

ctx.create(AUTHOR, Author.class).trait("logged").build();
// logs: "base", then "trait"
```

### 12. Batch Creation

Create multiple entities at once with `.times()`:

```java
// Create 5 authors
List<Author> authors = ctx.create(AUTHOR, Author.class).times(5);

// With per-item customization
List<Author> authors = ctx.create(AUTHOR, Author.class)
    .times(3, (builder, i) -> builder.set(AUTHOR.NAME, "Author " + i));
// "Author 0", "Author 1", "Author 2"

// Works with traits and definitions
List<Author> europeans = ctx.create(AUTHOR, Author.class)
    .trait("european")
    .times(10);
```

### 13. Factory Inheritance

Define parent/child factory definitions with automatic merging:

```java
// Parent definition
ctx.define("baseAuthor", AUTHOR, f -> {
    f.set(AUTHOR.NAME, "Base Author");
    f.set(AUTHOR.COUNTRY, "US");
    f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
    f.afterCreate(record -> log.info("Created author"));
});

// Child inherits defaults, traits, and callbacks
ctx.define(AUTHOR, f -> {
    f.parent("baseAuthor");
    f.set(AUTHOR.NAME, "Child Author"); // overrides parent's NAME
    // COUNTRY="US" inherited, "european" trait inherited, afterCreate inherited
});

Author author = ctx.create(AUTHOR, Author.class).build();
// name="Child Author" (child), country="US" (parent)

Author eu = ctx.create(AUTHOR, Author.class).trait("european").build();
// country="DE" (inherited trait)
```

**Merge rules:**
- Defaults: child overrides parent
- Generators: child overrides parent
- Traits: child overrides parent traits with same name
- Callbacks: concatenated (parent first, then child)

Missing parent definitions throw `IllegalStateException`. Cyclic inheritance (A → B → A) is detected and throws.

### 14. Build Strategies

Control how entities are built:

```java
// Build without inserting into database (no FK auto-creation, no callbacks)
AuthorRecord record = ctx.createRecord(AUTHOR)
    .set(AUTHOR.NAME, "Preview")
    .buildWithoutInsert();
// record is populated but NOT in the database

// Inspect resolved attributes as a map
Map<Field<?>, Object> attrs = ctx.createRecord(AUTHOR)
    .trait("european")
    .buildAttributes();
// attrs contains {AUTHOR.NAME -> "...", AUTHOR.COUNTRY -> "DE", ...}
```

### 15. Transient Attributes

Pass non-persisted data to lifecycle callbacks:

```java
ctx.define(AUTHOR, f -> {
    f.set(AUTHOR.NAME, "Author");
    f.afterCreate((record, transients) -> {
        int bookCount = transients.getOrDefault("bookCount", Integer.class, 0);
        UUID authorId = (UUID) record.get(AUTHOR.ID);
        for (int i = 0; i < bookCount; i++) {
            ctx.create(BOOK, Book.class).set(BOOK.AUTHOR_ID, authorId).build();
        }
    });
});

// Pass transient values at build time
Author author = ctx.create(AUTHOR, Author.class)
    .transientAttr("bookCount", 3)
    .build();
// 3 books auto-created via callback
```

### 16. Custom Value Generators

Register custom generators for specific fields or types:

```java
// For a specific field
ctx.registerGenerator(USER.EMAIL, new EmailGenerator());
// → "test-1@example.com", "test-2@example.com"

// For a type
ctx.registerGenerator(LocalDate.class, new LocalDateGenerator());

// For enums (override default)
ctx.registerGenerator(OrderStatus.class, (len, unique) -> OrderStatus.SHIPPED);

// Per-builder override
Author author = ctx.create(AUTHOR, Author.class)
    .withGenerator(AUTHOR.NAME, (len, unique) -> "Custom Name")
    .build();
```

### 17. Creating JootContext

`JootContext` is created from a jOOQ `DSLContext`. Choose the approach based on your test lifecycle needs:

#### Approach 1: Per-Test (Recommended)

Create `JootContext` in `@BeforeEach` for test isolation:

```java
class MyTest extends BaseIntegrationTest {
    JootContext ctx;
    
    @BeforeEach
    void setup() {
        ctx = JootContext.create(dsl);
        // Optional: register custom generators
        ctx.registerGenerator(MyType.class, (len, unique) -> ...);
    }
    
    @Test
    void myTest() {
        Author author = ctx.create(AUTHOR, Author.class).build();
    }
}
```

**Pros:**
- ✅ Complete isolation between tests
- ✅ No shared state
- ✅ Works with any test lifecycle (PER_METHOD, PER_CLASS)

**Cons:**
- ⚠️ Custom generators need re-registration (but this is fast ~0.01ms)

**Best for:** Most use cases. Context creation is lightweight (~0.1ms).

#### Approach 2: Per-Class (Shared)

Create `JootContext` in `@BeforeAll` when using static `DSLContext`:

```java
@TestInstance(Lifecycle.PER_CLASS)
class MyTest extends BaseIntegrationTest {
    static JootContext ctx;
    
    @BeforeAll
    static void setup() {
        ctx = JootContext.create(dsl);
        // Register generators once
        ctx.registerGenerator(MyType.class, (len, unique) -> ...);
    }
    
    @Test
    void myTest() {
        Author author = ctx.create(AUTHOR, Author.class).build();
    }
}
```

**Pros:**
- ✅ Custom generators registered once
- ✅ Slightly faster (negligible difference)

**Cons:**
- ⚠️ **Shared state:** Custom generators registered in one test affect others
- ⚠️ Requires static `DSLContext` and `PER_CLASS` lifecycle

**Best for:** Tests with common generator configuration that doesn't change.

**⚠️ Important:** `JootContext` is mostly stateless, but `GeneratorRegistry` (custom generators) is shared state. If tests register different generators, use Approach 1.

### 18. Database-Generated Values

Joot respects database-generated values:

```java
// SERIAL, AUTO_INCREMENT, IDENTITY columns
Article article = ctx.create(ARTICLE, Article.class).build();
assertThat(article.getId()).isNotNull();  // Generated by DB

// DEFAULT values (e.g., DEFAULT CURRENT_TIMESTAMP)
assertThat(article.getPublishedAt()).isNotNull();  // Set by DB
```

---

## 📚 Complete Example

```java
class BookServiceTest extends BaseIntegrationTest {
    
    private JootContext ctx;
    
    @Autowired
    private BookService bookService;
    
    @BeforeEach
    void setup() {
        ctx = JootContext.create(dsl);
    }
    
    @Test
    void shouldPublishBook() {
        // ARRANGE: Create book with specific title
        Book book = ctx.create(BOOK, Book.class)
            .set(BOOK.TITLE, "1984")
            .set(BOOK.PUBLISHED, false)
            .build();
        // Author, publisher, etc. auto-created ✨
        
        // ACT
        bookService.publish(book.getId());
        
        // ASSERT
        Book updated = ctx.get(book.getId(), BOOK, Book.class);
        assertThat(updated.getPublished()).isTrue();
    }
    
    @Test
    void shouldFindBooksByAuthor() {
        // ARRANGE: Create author with multiple books
        Author author = ctx.create(AUTHOR, Author.class).build();
        
        ctx.create(BOOK, Book.class)
            .set(BOOK.AUTHOR_ID, author.getId())
            .set(BOOK.TITLE, "Book 1")
            .build();
            
        ctx.create(BOOK, Book.class)
            .set(BOOK.AUTHOR_ID, author.getId())
            .set(BOOK.TITLE, "Book 2")
            .build();
        
        // ACT
        List<Book> books = bookService.findByAuthor(author.getId());
        
        // ASSERT
        assertThat(books).hasSize(2);
        assertThat(books).extracting(Book::getTitle)
            .containsExactlyInAnyOrder("Book 1", "Book 2");
    }
}
```

---

## 🎨 Built-in Generators

Joot comes with smart generators for common types:

| Type | Example Output | Notes |
|------|----------------|-------|
| `String` | `"name_1"`, `"email_2"` | Uses field name as prefix |
| `Integer`/`Long` | `1`, `2`, `3` | Auto-incremented |
| `UUID` | `UUID.randomUUID()` | Unique UUIDs |
| `Boolean` | `true`/`false` | Random |
| `LocalDateTime` | `LocalDateTime.now()` | Current timestamp |
| `LocalDate` | `LocalDate.now()` | Current date |
| `BigDecimal` | Random value | Suitable for prices |
| **`Enum`** | **First value** | **`enum.values()[0]` (deterministic)** |

### Adaptive String Generation

Strings adapt to column constraints:

```java
// VARCHAR(5)  → "a1", "b2", "c3"
// VARCHAR(10) → "n1", "e2" (first char + counter)
// VARCHAR(20) → "name_1", "title_2"
// VARCHAR(255) → "author_name_12345678"
// TEXT → "bio_1", "description_2"

// UNIQUE fields get unique values automatically
// author.email = "email_1", "email_2", "email_3"
```

---

## 🛠️ API Reference

### JootContext

```java
// Create context
JootContext ctx = JootContext.create(dsl);

// Factory definitions
ctx.define(TABLE, f -> { ... });
ctx.define("name", TABLE, f -> { f.parent("parentName"); ... });

// Sequences
ctx.sequence(FIELD, n -> "value" + n);

// Create entities
<T> T create(Table<?> table, Class<T> pojoClass).build();
<R extends Record> R createRecord(Table<R> table).build();

// Data access
<T> T get(Object pk, Table<?> table, Class<T> pojoClass);

// Nullable fields control
ctx.generateNullables(false);  // globally skip nullable fields

// Custom generators
<T> void registerGenerator(Field<T> field, ValueGenerator<T> generator);
<T> void registerGenerator(Class<T> type, ValueGenerator<T> generator);
```

### PojoBuilder / RecordBuilder

```java
// Set explicit values
builder.set(FIELD, value)

// Apply trait from definition
builder.trait("traitName")

// Control nullable generation
builder.generateNullables(boolean)

// Per-builder generator
builder.withGenerator(FIELD, generator)

// Batch creation
List<T> times(int count)
List<T> times(int count, (builder, index) -> { ... })

// Transient attributes
builder.transientAttr("name", value)

// Build strategies
T build()                           // insert + return
T buildWithoutInsert()              // no insert, no FK auto-creation
Map<Field<?>, Object> buildAttributes()  // resolved values map
```

---

## 🔧 Requirements

- **Java:** 17+
- **jOOQ:** 3.13+
- **Database:** Any jOOQ-supported database (PostgreSQL, MySQL, H2, etc.)

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Setup

```bash
git clone https://github.com/jtestkit/joot.git
cd joot
./gradlew test
```

---

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details

---

## 🙏 Acknowledgments

- Built on top of [jOOQ](https://www.jooq.org/) - excellent type-safe SQL library
- Inspired by [TestContainers](https://www.testcontainers.org/) - great testing philosophy
- Special thanks to the jOOQ community

---

## 🎯 Philosophy

**Joot is designed with these principles:**

1. **Zero boilerplate** - tests should focus on business logic, not data setup
2. **Production-like data** - tests with realistic data catch more bugs
3. **Type-safety** - leverage jOOQ's compile-time safety
4. **Simplicity** - one dependency, zero configuration
5. **Flexibility** - sensible defaults, full control when needed

---

## 💬 Support

- **Issues:** [GitHub Issues](https://github.com/jtestkit/joot/issues)
- **Discussions:** [GitHub Discussions](https://github.com/jtestkit/joot/discussions)

---

**Happy Testing!** 🚀

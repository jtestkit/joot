# Реализованные фичи Joot Framework

**Дата обновления:** 2026-01-04  
**Версия:** 0.9.0  
**Прогресс:** 11 из 11 фаз (100%) ✅

**Ключевые достижения:**
- ✅ Все генераторы зарегистрированы по умолчанию
- ✅ Семантические имена полей (`name_1` вместо `generated_12345678`)
- ✅ 148 интеграционных тестов (100% pass rate)

---

## ✅ Фаза 0: Подготовка инфраструктуры

### Инфраструктура
- ✅ Gradle проект с Java 17 target
- ✅ jOOQ code generation (DDLDatabase)
- ✅ TestContainers для PostgreSQL
- ✅ Автоматическая загрузка schema между тестами

**Файлы:**
- `build.gradle`
- `src/test/resources/test-schema.sql`
- `src/test/java/integration/BaseIntegrationTest.java`

---

## ✅ Фаза 1: Минимальный MVP

### 1.1 JootContext - основной API
```java
JootContext ctx = JootContext.create(dsl);
DSLContext dsl = ctx.dsl();
```

### 1.2 Создание простых сущностей
```java
Author author = ctx.create(AUTHOR, Author.class).build();
```
- Автогенерация NOT NULL полей (String, Integer, UUID, Boolean, etc)
- Конвертация Record → POJO

### 1.3 Cleanup механизм
```java
ctx.cleanup();  // Удаляет в обратном порядке (LIFO)
```

**Тесты:** 7 тестов
- `JootContextCreationTest` (1)
- `SimpleEntityCreationTest` (5)
- `CleanupTest` (1)

---

## ✅ Фаза 2: FK Auto-creation

### 2.1 MetadataAnalyzer
- Извлечение FK из jOOQ таблиц
- Определение primary keys

### 2.2 Автоматическое создание FK зависимостей
```java
// Создание Book автоматически создаёт Author
Book book = ctx.create(BOOK, Book.class).build();
assertThat(book.getAuthorId()).isNotNull();
```
- Рекурсивное создание parent entities
- Работает для любой глубины вложенности

### 2.2.1 RecordBuilder - надёжное FK creation
```java
AuthorRecord record = ctx.createRecord(AUTHOR).build();
```
- 100% надёжность (Record всегда существует)
- Нет зависимости от naming conventions
- `PojoBuilder` делегирует в `RecordBuilder`

### 2.3 Circular Dependency Detection

#### Нерешаемые циклы (NOT NULL ↔ NOT NULL)
```java
// person.company_id (NOT NULL) ↔ company.ceo_id (NOT NULL)
ctx.create(PERSON, Person.class).build();
// Выбрасывает CircularDependencyException
```

#### Решаемые циклы (NULLABLE ↔ NOT NULL)
```java
// users.default_team_id (nullable) ↔ team.owner_user_id (NOT NULL)
Team team = ctx.create(TEAM, Team.class).build();
// Цикл разрывается: users.default_team_id = NULL
```

#### Self-reference
```java
// category.parent_id → category.id (nullable)
Category cat = ctx.create(CATEGORY, Category.class).build();
// С generateNullables=true: создаётся parent с depth=1
// С generateNullables=false: parent_id = NULL
```

### 2.3.1 CreationChain - refactoring
- Immutable value object для tracking цепочки создания
- Чистая архитектура без package-private методов
- Нет downcast

**Тесты:** 15 тестов
- `ForeignKeyAutoCreationTest` (3)
- `CircularDependencyTest` (3)
- `ResolvableCircularDependencyTest` (4)
- `SelfReferenceTest` (5)

---

## ✅ Фаза 3: Nullable поля и FK

### 3.1-3.3 generateNullables - флаг для nullable полей

**Default:** `true` (production-like objects)

#### Глобальная настройка
```java
ctx.generateNullables(false);  // Минимализм
```

#### Per-builder override
```java
Book book = ctx.create(BOOK, Book.class)
    .generateNullables(true)  // Переопределяет глобальную настройку
    .build();
```

**Приоритет:** per-builder > context > default(true)

### 3.4 Nullable FK auto-creation
```java
// product.id ← order.product_id (nullable)
Order order = ctx.create(ORDERS, Order.class).build();

// С generateNullables=true (default): Product создан автоматически
assertThat(order.getProductId()).isNotNull();

// С generateNullables=false: product_id = NULL
```

**Особенности:**
- Циклические nullable FK всегда `NULL` (игнорируют `generateNullables`)
- Self-reference с `generateNullables=true` → parent с depth=1

**Тесты:** 11 тестов
- `NullableFieldsTest` (3)
- `NullableForeignKeyTest` (3)
- `SelfReferenceTest` (5) - уже включены в Фазу 2

---

## ✅ Фаза 4: UNIQUE Constraints

### 4.1 Автоматическая генерация уникальных значений

Joot автоматически определяет UNIQUE поля и генерирует уникальные значения:

```java
// author.email VARCHAR(255) UNIQUE
Author author1 = ctx.create(AUTHOR, Author.class).build();
Author author2 = ctx.create(AUTHOR, Author.class).build();

// Гарантированно разные email адреса
assertThat(author1.getEmail()).isNotEqualTo(author2.getEmail());
```

**Механизм:**
- `AtomicLong` counter для UNIQUE полей
- String: `"unq_1"`, `"unq_2"`, ...
- Integer/Long: `1`, `2`, `3`, ...
- Thread-safe (concurrent tests)

### 4.2 FK Auto-Creation с UNIQUE

При автоматическом создании parent entities с UNIQUE полями не возникает коллизий:

```java
// book.author_id → author(id)
// author.email UNIQUE ← Автоматически уникальное!

Book book1 = ctx.create(BOOK, Book.class).build();
Book book2 = ctx.create(BOOK, Book.class).build();

// Каждая книга автоматически создаёт своего автора
// Нет IntegrityConstraintViolationException на email
assertThat(book1.getAuthorId()).isNotEqualTo(book2.getAuthorId());
```

### 4.3 Адаптивная генерация длины строк

**Проблема:** Сгенерированные строки могут превышать длину колонки:
```
ERROR: value too long for type character varying(10)
```

**Решение:** Адаптивные форматы в зависимости от `VARCHAR(N)`:

| Длина | Формат (обычные) | Формат (UNIQUE) | Пример |
|-------|------------------|-----------------|--------|
| ≤ 5   | `a42`, `g17`     | `a1`, `b2`      | `VARCHAR(3)` → `"a42"` |
| ≤ 10  | `g1234`          | `u1`, `u2`      | `VARCHAR(10)` → `"u1"` |
| ≤ 20  | `gen_a3f9`       | `unq_1`         | `VARCHAR(20)` → `"unq_1"` |
| ≤ 100 | `generated_...`  | `unq_1_a3f9`    | `VARCHAR(50)` → полный формат |
| TEXT  | `generated_...`  | `unq_1_a3f9`    | Без ограничений |

**Особенности:**
- Автоматический truncate до `maxLength`
- Rotating prefixes (`a`-`z`) для коротких полей
- Никогда не возникает database error из-за длины

**Пример:**
```java
// tiny_unique VARCHAR(5) UNIQUE
StringLengthTest entity1 = ctx.create(STRING_LENGTH_TEST, StringLengthTest.class).build();
StringLengthTest entity2 = ctx.create(STRING_LENGTH_TEST, StringLengthTest.class).build();

// entity1.tinyUnique = "a1" (2 chars)
// entity2.tinyUnique = "b2" (2 chars)
// Никогда не превышает 5 chars!
```

**Тесты:** 15 тестов
- `UniqueConstraintTest` (5)
- `StringLengthAdaptiveTest` (10)

**Документация:**
- `docs/UNIQUE_CONSTRAINTS.md`
- `docs/ADAPTIVE_STRING_LENGTH.md`

---

## ✅ Фаза 5: ValueGenerator и кастомизация

### 5.1 Рефакторинг ValueGenerator

**Проблема:** Исходный `T generate()` не давал доступа к metadata (длина, UNIQUE).

**Решение:** Два метода в интерфейсе:

```java
public interface ValueGenerator<T> {
    // Простой метод (реализуют все генераторы)
    T generate(int maxLength, boolean isUnique);
    
    // Продвинутый метод с доступом к Field/Table
    default T generate(Field<T> field, Table<?> table) {
        int maxLength = field.getDataType().length();
        boolean isUnique = /* извлекается из metadata */;
        return generate(maxLength, isUnique);
    }
}
```

**Преимущества:**
- ✅ Простые генераторы остаются простыми (реализуют только первый метод)
- ✅ Продвинутые генераторы могут override второй метод (semantic generation)
- ✅ Встроенные генераторы обновлены (Integer, Long, UUID, Boolean)
- ✅ String НЕ зарегистрирован (сохранена адаптивная логика)

### 5.2 Глобальные кастомные генераторы

**API для регистрации:**

```java
// Field-specific (самый высокий приоритет)
ctx.registerGenerator(BOOK.ISBN, (len, uniq) -> "978-" + UUID.randomUUID());

// Type-based (для всех полей типа)
ctx.registerGenerator(Integer.class, (len, uniq) -> 42);
```

**Приоритеты:**
1. Field-specific generator
2. Type-based generator
3. Built-in fallback (adaptive String logic)

### 5.3 Per-builder генераторы (.withGenerator)

**Зачем:** Кастомная генерация для ОДНОГО создания (не затрагивает остальные).

**Use cases:**
- Негативные тесты (невалидные данные)
- Edge cases (граничные значения)
- A/B тестирование

**Пример:**
```java
// Для ЭТОЙ книги - невалидный ISBN
Book invalidBook = ctx.create(BOOK, Book.class)
    .withGenerator(BOOK.ISBN, (len, uniq) -> "INVALID-ISBN")
    .build();

// Другие книги - обычная логика
Book normalBook = ctx.create(BOOK, Book.class).build();
```

**Финальные приоритеты генерации:**
```
1. Explicit .set(FIELD, value)           ← Самый высокий
2. Per-builder .withGenerator(FIELD, ...)  
3. Field-specific ctx.registerGenerator(FIELD, ...)
4. Type-based ctx.registerGenerator(CLASS, ...)
5. Built-in fallback (adaptive String)    ← Самый низкий
```

**Тесты:** 15 тестов
- `CustomGeneratorTest` (6) - глобальные генераторы
- `WithGeneratorTest` (9) - per-builder генераторы

**Файлы:**
- `src/main/java/io/github/jtestkit/joot/ValueGenerator.java`
- `src/main/java/io/github/jtestkit/joot/GeneratorRegistry.java`
- `src/main/java/io/github/jtestkit/joot/generators/*.java`
- `src/test/java/integration/CustomGeneratorTest.java`
- `src/test/java/integration/WithGeneratorTest.java`

---

## ✅ Фаза 6: Доступ к данным

### 6.1 ctx.get() и ctx.getAll()

#### Получение по PK
```java
Author author = ctx.get(authorId, AUTHOR, Author.class);
// Возвращает null если не найдено (не exception)
```

#### Получение всех сущностей
```java
List<Book> books = ctx.getAll(BOOK, Book.class);
// Возвращает пустой список если нет данных (не null)
```

**Преимущества:**
- Удобнее чем `ctx.dsl().select...`
- Стандартизированный API
- Thread-safe

**Тесты:** 6 тестов
- `DataAccessTest` (6)

**Документация:** `docs/DATA_ACCESS_API.md`

---

## ✅ Фаза 7: JUnit интеграция

### 7.1 @JootTest - автоматическая инъекция JootContext

**Зачем:** Убрать boilerplate код для создания JootContext.

**До (ручная настройка):**
```java
class MyTest extends BaseIntegrationTest {
    private JootContext ctx;
    
    @BeforeEach
    void setup() {
        ctx = JootContext.create(dsl);  // Ручная инициализация
    }
    
    @AfterEach
    void cleanup() {
        ctx.cleanup();  // Ручная очистка
    }
    
    @Test
    void myTest() {
        Author author = ctx.create(AUTHOR, Author.class).build();
        // ...
    }
}
```

**После (@JootTest):**
```java
@JootTest  // ← Автоинъекция!
class MyTest extends BaseJootTest {
    @Joot  // ← Инъекция
    private JootContext ctx;
    
    @AfterEach
    void cleanup() {
        ctx.cleanup();  // ← Ваш выбор, когда cleanup
    }
    
    @Test
    void myTest() {
        Author author = ctx.create(AUTHOR, Author.class).build();
        // ...
    }
}
```

**Экономия:** 10 строк → 6 строк (-40% boilerplate для setup)

### 7.2 Компоненты

#### @JootTest annotation
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(JootExtension.class)
public @interface JootTest {}
```

Включает автоматическую инъекцию для тестового класса.

#### @Joot annotation
```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Joot {}
```

Маркирует поля для инъекции `JootContext`.

#### JootExtension
```java
public class JootExtension implements BeforeTestExecutionCallback {
    // Инъекция JootContext перед тестом
    // Cleanup - ответственность пользователя
}
```

**Ключевая деталь:** Использует `BeforeTestExecutionCallback` (не `BeforeEachCallback`), 
чтобы вызываться ПОСЛЕ `@BeforeEach` (когда `DSLContext` уже инициализирован).

**Философия:** Joot = фабрика данных, не lifecycle manager. Управление жизненным циклом данных - ответственность пользователя.

#### BaseJootTest
```java
@Testcontainers
public abstract class BaseJootTest {
    @Container
    protected static PostgreSQLContainer<?> postgres = /* ... */;
    
    protected DSLContext dsl;
    
    @BeforeAll
    static void setupSchema() {
        // Создание схемы один раз для всех тестов
    }
    
    @BeforeEach
    void setupDSL() {
        // Создание DSLContext для каждого теста
    }
}
```

**Difference от `BaseIntegrationTest`:**
- ✅ Создаёт schema **один раз** (`@BeforeAll`)
- ✅ НЕ дропает таблицы между тестами
- ✅ Полагается на `ctx.cleanup()` для очистки данных
- ✅ **Быстрее** (нет пересоздания schema)

### 7.3 Execution Order

JUnit 5 порядок выполнения с `@JootTest`:

```
@BeforeAll → setupSchema()  ← Один раз для всех тестов
  ↓
[Per test method:]
  @BeforeEach → setupDSL()            ← Создание DSLContext
  ↓
  BeforeTestExecutionCallback         ← JootExtension: инъекция ctx
  ↓
  @Test → myTest()                    ← Выполнение теста
  ↓
  @AfterEach → cleanup()              ← Ваш код: ctx.cleanup()
  ↓
[Next test...]
  ↓
@AfterAll → teardown()      ← Container stops
```

### 7.4 Разные стратегии cleanup

Joot не навязывает способ управления данными. Вы выбираете:

```java
// Стратегия 1: Cleanup после каждого теста
@JootTest
class MyTest {
    @Joot private JootContext ctx;
    
    @AfterEach
    void cleanup() {
        ctx.cleanup();  // ✅ Изоляция между тестами
    }
}

// Стратегия 2: Shared data между тестами
@JootTest
class MyTest {
    @Joot private JootContext ctx;
    
    @AfterAll
    static void cleanup() {
        // Cleanup один раз после всех тестов
    }
}

// Стратегия 3: Транзакции с rollback
@JootTest
@Transactional
@Rollback
class MyTest {
    @Joot private JootContext ctx;
    // Rollback вместо cleanup
}

// Стратегия 4: Schema пересоздаётся
class MyTest extends BaseIntegrationTest {
    private JootContext ctx;
    // Schema дропается - cleanup не нужен
}
```

#### Error handling
- ✅ Если `DSLContext` не найден → понятное сообщение об ошибке
- ✅ Если `DSLContext` null → понятное сообщение об ошибке
- ✅ Если `@Joot` на неправильном типе → понятное сообщение об ошибке

### 7.5 Преимущества

| Аспект | Без @JootTest | С @JootTest |
|--------|---------------|-------------|
| **Setup boilerplate** | Ручное создание ctx | Автоинъекция |
| **Cleanup** | Ваш выбор | Ваш выбор |
| **Гибкость** | Полная | Полная |
| **Читаемость** | Setup отвлекает | Фокус на логике |
| **Скорость** | Schema пересоздаётся | Schema создаётся раз (с BaseJootTest) |

**Тесты:** 4 теста
- `JUnitIntegrationTest` (4)
  - `shouldInjectJootContext()` - инъекция работает
  - `shouldWorkWithAutoCreatedFK()` - FK auto-creation работает
  - `shouldCleanupAfterTest()` - cleanup работает (явный)
  - `shouldHaveCleanStateInEachTest()` - изоляция между тестами

**Файлы:**
- `src/main/java/io/github/jtestkit/joot/JootTest.java`
- `src/main/java/io/github/jtestkit/joot/Joot.java`
- `src/main/java/io/github/jtestkit/joot/JootExtension.java`
- `src/test/java/integration/BaseJootTest.java`
- `src/test/java/integration/JUnitIntegrationTest.java`
- `build.gradle` (fix: compileTestJava зависит от generateTestJooq)

**Документация:** `docs/JUNIT_INTEGRATION.md`

**Архитектурные решения:**
- ✅ Две аннотации для явности (следуя паттерну Spring/Mockito)
- ✅ Только инъекция, никакого автоматического cleanup
- ✅ Соответствие SRP: Joot создаёт данные, не управляет lifecycle

---

## 📊 Статистика

| Метрика | Значение |
|---------|----------|
| **Фаз реализовано** | 11 ✅ |
| **Всего тестов** | 148 интеграционных |
| **Тесты проходят** | 148 (100%) ✅ |
| **Строк кода (main)** | ~2000 |
| **Строк кода (tests)** | ~2200 |
| **Покрытие** | Все публичные API покрыты тестами |

---

## 📁 Структура проекта

```
src/main/java/io/github/jtestkit/joot/
├── JootContext.java              # Основной API интерфейс
├── JootContextImpl.java          # Реализация JootContext
├── JootTest.java                 # Аннотация для JUnit интеграции
├── Joot.java                     # Аннотация для инъекции поля
├── JootExtension.java            # JUnit 5 extension для инъекции
├── PojoBuilder.java              # Builder для POJO
├── PojoBuilderImpl.java          # Реализация PojoBuilder
├── RecordBuilder.java            # Builder для Record
├── RecordBuilderImpl.java        # Реализация RecordBuilder (~420 строк)
├── MetadataAnalyzer.java         # Извлечение FK metadata
├── CreationChain.java            # Tracking circular dependencies
├── CyclicDependencyResolver.java # Resolver для cycles
├── CircularDependencyException.java  # Exception для циклов
├── ValueGenerator.java           # Интерфейс для генераторов
├── GeneratorRegistry.java        # Registry для кастомных генераторов
└── generators/
    ├── IntegerGenerator.java         # Встроенный генератор Integer
    ├── LongGenerator.java            # Встроенный генератор Long
    ├── UuidGenerator.java            # Встроенный генератор UUID
    ├── BooleanGenerator.java         # Встроенный генератор Boolean
    ├── StringGenerator.java          # Reference (не используется)
    ├── EmailGenerator.java           # Utility: генератор Email
    ├── PhoneGenerator.java           # Utility: генератор Phone
    ├── LocalDateTimeGenerator.java   # Utility: генератор LocalDateTime
    └── LocalDateGenerator.java       # Utility: генератор LocalDate

src/test/java/integration/
├── BaseIntegrationTest.java      # Базовый класс для ручной setup
├── BaseJootTest.java             # Базовый класс для @JootTest
├── JootContextCreationTest.java  # 1 тест
├── SimpleEntityCreationTest.java # 5 тестов
├── CleanupTest.java              # 1 тест
├── ForeignKeyAutoCreationTest.java # 3 теста
├── CircularDependencyTest.java   # 3 теста
├── ResolvableCircularDependencyTest.java # 4 теста
├── SelfReferenceTest.java        # 5 тестов
├── RecordBuilderTest.java        # 3 теста
├── NullableFieldsTest.java       # 3 теста
├── NullableForeignKeyTest.java   # 3 теста
├── UniqueConstraintTest.java     # 5 тестов
├── StringLengthAdaptiveTest.java # 10 тестов
├── MultipleForeignKeysTest.java  # 5 тестов
├── DataAccessTest.java           # 6 тестов
├── CustomGeneratorTest.java      # 6 тестов
├── WithGeneratorTest.java        # 9 тестов
├── JUnitIntegrationTest.java     # 4 теста для @JootTest
├── SequenceSupportTest.java      # 5 тестов для SERIAL/sequences
└── UtilityGeneratorsTest.java    # 9 тестов для utility генераторов

**Итого: 97 интеграционных тестов, все проходят ✅**

docs/
├── IMPLEMENTATION_STEPS.md       # Детальный план реализации (TDD)
├── COMPLETED_FEATURES.md         # Этот файл - summary всех фич
├── DATA_ACCESS_API.md            # Документация Data Access API
├── UNIQUE_CONSTRAINTS.md         # Документация UNIQUE полей
├── ADAPTIVE_STRING_LENGTH.md     # Документация адаптивной генерации
└── JUNIT_INTEGRATION.md          # Документация JUnit integration

build.gradle
└── Fix: compileTestJava → dependsOn → generateTestJooq
    (автоматическая генерация jOOQ кода при clean build)
```

---

## ✅ Фаза 8: Sequences Support

### 8.1 Database-Generated Values

**Проблема:** Joot генерировал значения для ВСЕХ полей, включая те, которые БД должна генерировать сама (SERIAL, DEFAULT).

**Решение:** Автоматическое определение и пропуск database-generated полей.

```java
// Поддерживаемые типы:
// - SERIAL / AUTO_INCREMENT / IDENTITY
// - DEFAULT values (e.g. DEFAULT CURRENT_TIMESTAMP)

CREATE TABLE article (
    id SERIAL PRIMARY KEY,                              -- ← БД генерирует
    title VARCHAR(255) NOT NULL,
    published_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP    -- ← БД генерирует
);

Article article = ctx.create(ARTICLE, Article.class).build();
// article.id = 1 (сгенерировано БД)
// article.publishedAt = 2026-01-02 18:40:00 (сгенерировано БД)
```

**Механизм:**
```java
private boolean shouldSkipGeneration(Field<?> field) {
    DataType<?> dataType = field.getDataType();
    
    // Skip identity columns (SERIAL, AUTO_INCREMENT, IDENTITY)
    if (dataType.identity()) {
        return true;
    }
    
    // Skip fields with DEFAULT values
    if (dataType.defaulted()) {
        return true;
    }
    
    return false;
}
```

**Преимущества:**
- ✅ Работает с PostgreSQL SERIAL
- ✅ Работает с MySQL AUTO_INCREMENT
- ✅ Работает с Oracle IDENTITY
- ✅ Работает с DEFAULT значениями
- ✅ Можно переопределить через `.set()`

**Тесты:** 5 тестов
- `SequenceSupportTest` (5)
  - `shouldCreateArticleWithSerialId()` - SERIAL работает
  - `shouldGenerateSequentialIds()` - ID последовательные
  - `shouldRespectDefaultTimestamp()` - DEFAULT работает
  - `shouldAllowExplicitIdOverride()` - можно переопределить
  - `shouldCleanupSequenceBasedRecords()` - cleanup работает

**Файлы:**
- `src/main/java/io/github/jtestkit/joot/RecordBuilderImpl.java`
- `src/test/resources/test-schema.sql` (таблица `article`)
- `src/test/java/integration/SequenceSupportTest.java`

---

## ✅ Utility Generators

### 9.1 Built-in Generators для популярных типов

**Проблема:** Некоторые типы данных встречаются очень часто, но требуют ручной регистрации генераторов.

**Решение:** Готовые utility генераторы для 90% use cases.

### 🎯 Автоматически работают из коробки (Default Generators)

#### LocalDateTimeGenerator ✅
```java
// Регистрация НЕ нужна - работает автоматически!
Contact contact = ctx.create(CONTACT, Contact.class).build();
// contact.registeredAt = 2026-01-02 23:20:00
```

- **Зарегистрирован по умолчанию** в `GeneratorRegistry`
- Non-unique fields: `LocalDateTime.now()`
- Unique fields: `LocalDateTime.now().minusSeconds(counter)`
- Ensures uniqueness by going backwards in time

#### LocalDateGenerator ✅
```java
// Регистрация НЕ нужна - работает автоматически!
Contact contact = ctx.create(CONTACT, Contact.class).build();
// contact.birthDate = 2026-01-02
```

- **Зарегистрирован по умолчанию** в `GeneratorRegistry`
- Non-unique fields: `LocalDate.now()`
- Unique fields: `LocalDate.now().minusDays(counter)`
- Ensures uniqueness by going backwards in time

### 📦 Utility Generators (требуют регистрации)

#### EmailGenerator
```java
// Регистрация НУЖНА (field-specific)
ctx.registerGenerator(CONTACT.EMAIL, new EmailGenerator());

Contact contact = ctx.create(CONTACT, Contact.class).build();
// contact.email = "test-1@example.com"
```

- Format: `test-{counter}@example.com`
- Thread-safe (AtomicLong)
- Respects maxLength constraint

#### PhoneGenerator
```java
// Регистрация НУЖНА (field-specific)
ctx.registerGenerator(CONTACT.PHONE, new PhoneGenerator());

Contact contact = ctx.create(CONTACT, Contact.class).build();
// contact.phone = "+1-555-0101"
```

- Format: `+1-555-{counter}` (US test numbers)
- Uses reserved range 555-0100 to 555-0199
- Adjusts format for short fields

### 🔄 Комплексный пример
```java
JootContext ctx = JootContext.create(dsl);

// Регистрируем только Email и Phone (даты работают автоматически!)
ctx.registerGenerator(CONTACT.EMAIL, new EmailGenerator());
ctx.registerGenerator(CONTACT.PHONE, new PhoneGenerator());

// Использование - всё работает!
Contact contact = ctx.create(CONTACT, Contact.class).build();
// ✅ email = "test-1@example.com"         (EmailGenerator)
// ✅ phone = "+1-555-0101"                (PhoneGenerator)
// ✅ registeredAt = 2026-01-02 23:20:00   (автоматически!)
// ✅ birthDate = 2026-01-02               (автоматически!)
```

### 📋 Итоговая таблица генераторов

| Тип | По умолчанию? | Когда использовать |
|-----|---------------|-------------------|
| `String` | ✅ **Да (Adaptive!)** | Автоматически адаптируется к длине колонки |
| `LocalDateTime` | ✅ Да | Всегда работает |
| `LocalDate` | ✅ Да | Всегда работает |
| `Integer/Long` | ✅ Да | Всегда работает |
| `UUID` | ✅ Да | Всегда работает |
| `Boolean` | ✅ Да | Всегда работает |
| `Email` (String) | ❌ Нет | Field-specific generator для email полей |
| `Phone` (String) | ❌ Нет | Field-specific generator для phone полей |

### ⚡ Архитектурное улучшение (Рефакторинг)

**Проблема 1:** Adaptive логика для String была "застряла" в `RecordBuilderImpl`, не переиспользовалась.

**Решение:** Вся adaptive логика перенесена в `StringGenerator`:
- ✅ `StringGenerator` зарегистрирован по умолчанию
- ✅ Использует `generate(Field<T>, Table<?>)` для получения контекста
- ✅ Автоматически определяет `maxLength` и `UNIQUE` constraint
- ✅ Уменьшен размер `RecordBuilderImpl` на ~94 строки
- ✅ Чистая архитектура без дублирования кода

**Проблема 2:** Генерируемые строки не имели семантической связи с полем.

**До:**
```java
Author author = ctx.create(AUTHOR, Author.class).build();
// author.name = "generated_12345678"  ← Что это за поле?
// author.bio = "generated_87654321"   ← Что это за поле?
```

**После:**
```java
Author author = ctx.create(AUTHOR, Author.class).build();
// author.name = "name_1"    ← Понятно, что это name!
// author.bio = "bio_1"      ← Понятно, что это bio!

Book book = ctx.create(BOOK, Book.class).build();
// book.title = "title_1"    ← Понятно, что это title!
// book.isbn = "isbn_1"      ← Понятно, что это ISBN!
```

**Преимущества:**
- ✅ **Читаемость в тестах** - видно, какое поле было сгенерировано
- ✅ **Отладка проще** - понятно, откуда значение
- ✅ **Семантическая связь** - имя поля используется как префикс
- ✅ **Нет breaking changes** - старый API `generate(int, boolean)` работает

**Тесты:** 9 тестов
- `UtilityGeneratorsTest` (9)
  - Email generation & uniqueness
  - Phone generation & format
  - LocalDateTime generation (default)
  - LocalDate generation (default)
  - Length constraints respected
  - Manual override with `.set()`
  - Default generators work out-of-the-box
  - Multiple contacts integration

**Файлы:**
- `src/main/java/io/github/jtestkit/joot/GeneratorRegistry.java` (регистрирует LocalDateTime/LocalDate)
- `src/main/java/io/github/jtestkit/joot/generators/EmailGenerator.java`
- `src/main/java/io/github/jtestkit/joot/generators/PhoneGenerator.java`
- `src/main/java/io/github/jtestkit/joot/generators/LocalDateTimeGenerator.java`
- `src/main/java/io/github/jtestkit/joot/generators/LocalDateGenerator.java`
- `src/test/resources/test-schema.sql` (таблица `contact`)
- `src/test/java/integration/UtilityGeneratorsTest.java`

---

## ✅ Фаза 9: Factory Definitions + Traits

### 9.1 Factory Definitions
```java
ctx.define(AUTHOR, f -> {
    f.set(AUTHOR.NAME, "Isaac Asimov");
    f.set(AUTHOR.COUNTRY, "US");
});

Author author = ctx.create(AUTHOR, Author.class).build();
// author.getName() == "Isaac Asimov"
```
- Определение дефолтных значений для таблицы
- Определение генераторов внутри фабрики (`f.withGenerator()`)
- Переопределение через `.set()` на билдере
- Работает с PojoBuilder и RecordBuilder
- Обратная совместимость: без `define()` всё работает как раньше

### 9.2 Traits
```java
ctx.define(AUTHOR, f -> {
    f.set(AUTHOR.NAME, "Default");
    f.set(AUTHOR.COUNTRY, "US");
    f.trait("european", t -> t.set(AUTHOR.COUNTRY, "DE"));
    f.trait("renamed", t -> t.set(AUTHOR.NAME, "Special"));
});

Author eu = ctx.create(AUTHOR, Author.class).trait("european").build();
// eu.getCountry() == "DE"
```
- Именованные вариации поверх базовой фабрики
- Композиция нескольких трейтов (`.trait("a").trait("b")`)
- Порядок: последний трейт побеждает при конфликте полей
- Явный `.set()` всегда побеждает трейт
- Поддержка callbacks (`beforeCreate`, `afterCreate`) в трейтах

### Приоритет значений (обновлённый)
1. Explicit `.set()` (highest)
2. Per-builder `.withGenerator()`
3. Trait overrides (in activation order)
4. Definition defaults
5. Definition generators
6. Global field-specific generator
7. Global type-based generator
8. Enum handling
9. IllegalArgumentException

**Тесты:** 13 тестов
- `FactoryDefinitionTest` (6): define+create, override, без define, RecordBuilder, FK auto-creation, generators
- `TraitCompositionTest` (7): single trait, multiple traits, order, explicit override, RecordBuilder, FK, без трейтов

**Файлы:**
- `src/main/java/io/github/jtestkit/joot/Trait.java`
- `src/main/java/io/github/jtestkit/joot/TraitBuilder.java`
- `src/main/java/io/github/jtestkit/joot/FactoryDefinition.java`
- `src/main/java/io/github/jtestkit/joot/FactoryDefinitionBuilder.java`
- `src/main/java/io/github/jtestkit/joot/FactoryDefinitionRegistry.java`
- `src/main/java/io/github/jtestkit/joot/JootContext.java` (добавлен `define()`)
- `src/main/java/io/github/jtestkit/joot/JootContextImpl.java` (registry + implement)
- `src/main/java/io/github/jtestkit/joot/RecordBuilder.java` (добавлен `trait()`)
- `src/main/java/io/github/jtestkit/joot/RecordBuilderImpl.java` (resolveDefinitionDefaults + callbacks)
- `src/main/java/io/github/jtestkit/joot/PojoBuilder.java` (добавлен `trait()`)
- `src/main/java/io/github/jtestkit/joot/PojoBuilderImpl.java` (transfer traits)

---

## ✅ Фаза 10: Sequences, Callbacks, Batch Creation

### 10.1 Sequences
```java
ctx.sequence(AUTHOR.EMAIL, n -> "author" + n + "@test.com");
// "author1@test.com", "author2@test.com", ...
```
- Sugar over `registerGenerator()` с AtomicLong counter
- Начинается с 1, автоинкремент
- Работает с любым типом (String, Integer, etc.)

### 10.2 Lifecycle Callbacks
```java
ctx.define(AUTHOR, f -> {
    f.beforeCreate(record -> { /* до INSERT */ });
    f.afterCreate(record -> { /* после INSERT, с PK */ });
});
```
- `beforeCreate` — модификация Record перед INSERT
- `afterCreate` — создание связанных сущностей, логирование
- Trait callbacks compose с base (base first, then trait)

### 10.3 Batch Creation
```java
List<Author> authors = ctx.create(AUTHOR, Author.class).times(5);
List<Author> custom = ctx.create(AUTHOR, Author.class)
    .times(3, (builder, i) -> builder.set(AUTHOR.NAME, "Author " + i));
```
- `times(count)` — создание N сущностей
- `times(count, customizer)` — с кастомизацией per-item (index 0-based)
- Каждая итерация — fresh builder через `cloneConfiguration()`
- Работает с define(), traits, FK auto-creation

**Тесты:** 16 тестов
- `SequenceGeneratorTest` (4): sequential values, override, with define, integer fields
- `CallbackTest` (5): beforeCreate, afterCreate, create children, trait+base compose, no define
- `BatchCreationTest` (7): times(5), RecordBuilder, customizer, define, trait, FK, times(1)

**Файлы:**
- `src/main/java/io/github/jtestkit/joot/JootContext.java` (добавлен `sequence()`)
- `src/main/java/io/github/jtestkit/joot/JootContextImpl.java` (реализация sequence)
- `src/main/java/io/github/jtestkit/joot/RecordBuilder.java` (добавлен `times()`)
- `src/main/java/io/github/jtestkit/joot/RecordBuilderImpl.java` (реализация times + cloneConfiguration)
- `src/main/java/io/github/jtestkit/joot/PojoBuilder.java` (добавлен `times()`)
- `src/main/java/io/github/jtestkit/joot/PojoBuilderImpl.java` (реализация times + cloneConfiguration)

---

## ✅ Фаза 11: Factory Inheritance, Build Strategies, Transients

### 11.1 Factory Inheritance
```java
ctx.define("baseAuthor", AUTHOR, f -> {
    f.set(AUTHOR.NAME, "Base Author");
    f.set(AUTHOR.COUNTRY, "US");
});

ctx.define(AUTHOR, f -> {
    f.parent("baseAuthor");
    f.set(AUTHOR.NAME, "Child Author"); // overrides parent
    // COUNTRY="US" inherited
});
```
- Named factory definitions (`define(name, table, ...)`)
- Parent-child inheritance via `parent(String)`
- Recursive merge: defaults (child wins), generators (child wins), traits (child wins by name), callbacks (concatenated)

### 11.2 Build Strategies
```java
AuthorRecord record = ctx.createRecord(AUTHOR)
    .buildWithoutInsert();  // no INSERT, no FK auto-creation, no callbacks

Map<Field<?>, Object> attrs = ctx.createRecord(AUTHOR)
    .buildAttributes();     // resolved field-value map, no Record or INSERT
```
- `buildWithoutInsert()` — populated Record without database interaction
- `buildAttributes()` — raw attribute map for inspection

### 11.3 Transient Attributes
```java
ctx.define(AUTHOR, f -> {
    f.afterCreate((record, transients) -> {
        int n = transients.getOrDefault("bookCount", Integer.class, 0);
        // create n books...
    });
});

ctx.create(AUTHOR, Author.class)
    .transientAttr("bookCount", 3)
    .build();
```
- Non-persisted values passed to callbacks via `TransientAwareCallback`
- `transientAttr(String, Object)` on builders
- `TransientAttributes.get()`, `getOrDefault()`, `has()`
- Works with both `beforeCreate` and `afterCreate`
- Works in traits

### 11.4 Bug Fixes
- Cycle detection in `mergeWithParent()` — предотвращает StackOverflow при циклических наследованиях
- Validation of missing parent — `IllegalStateException` если parent не зарегистрирован
- Validation of unknown traits — `IllegalArgumentException` с списком доступных трейтов
- Fix `generateNullables(false)` в PojoBuilderImpl — значение false терялось при передаче в delegate
- Cleanup: `Boolean` → `boolean` в PojoBuilderImpl, удалён мёртвый null-check

**Тесты:** 28 тестов
- `FactoryInheritanceTest` (7): parent defaults, callbacks, traits, override, generators, cyclic error, missing parent error
- `BuildStrategiesTest` (9): buildWithoutInsert Record/POJO, explicit set, buildAttributes, traits, POJO, no callbacks, traits with buildWithoutInsert, generators with buildAttributes
- `TransientAttributesTest` (8): afterCreate, create children, default null, trait callbacks, beforeCreate, RecordBuilder, inheritance, times
- `TraitCompositionTest` (+2): unknown trait error, trait without definition error
- `BatchCreationTest` (+1): times(0) edge case

**Файлы:**
- `src/main/java/io/github/jtestkit/joot/TransientAttributes.java`
- `src/main/java/io/github/jtestkit/joot/TransientAwareCallback.java`
- Updated: FactoryDefinition, FactoryDefinitionBuilder, FactoryDefinitionRegistry, Trait, TraitBuilder
- Updated: RecordBuilder/Impl, PojoBuilder/Impl (buildWithoutInsert, buildAttributes, transientAttr, validation)


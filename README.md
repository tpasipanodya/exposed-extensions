# Exposed Extensions
![CI & CD](https://github.com/tpasipanodya/exposed-extensions/actions/workflows/.github/workflows/cicd.yml/badge.svg)

A collection of extensions for [Exposed](https://github.com/tpasipanodya/Exposed). Includes:
- Logical tenant isolation, soft deletes and  (and more) via [Exposed](https://github.com/tpasipanodya/Exposed)'s default filters.
- Declarative mapping of objects to and from the database. This is similar to
  [Exposed's](https://github.com/tpasipanodya/Exposed) DAO DSL. The library simply provides a slightly 
  more flexible alternative. You define how entities and their associations should be mapped and those 
  rules can be applied to any query you execute.
- Postgres array & jsonb column support.

  
## How to Use
```kotlin
implementation("io.taff:exposed-extensions:0.12.0")
```

Using logical tenant isolation as an example:

```kotlin
/** 1. Declare your record. */
data class Book(
  var title: String? = null,
  var tenantId: Long? = null,
  override var id: Long? = null,
  override var createdAt: Instant? = null,
  override var updatedAt: Instant? = null
) : TenanatScopedRecord<Long, Long>

/** 2. Declare how it is stored */
val books = object : TenantScopedLongIdTable<Long, Book>() {
  val title = varchar("name", 50).nullable()
  override val tenantId: Column<Long> = long("author_id")
  
  /** defines how to map your record from a select statement */
  override fun initializeRecord(row: ResultRow) = Book(title = row[title])
  
  /** defines how to map your record to an insert/update statement */
  override fun appendStatementValues(stmt: UpdateBuilder<Int>, book: Book) {
    book.title?.let { stmt[title] = it }
  }
}

// 3. Connect & query :
Database.connect(env<String>("DB_URL"))
transaction { SchemaUtils.create(books) }


transaction {
  setCurrentTenantId(zeeyaMerali.id)
  books.insert(Book("A Big Bang in a Little Room")) 
  val zeeyaMeralisBooks = books.selectAll().map(books::toRecord)

  setCurrentTenantId(andyWeir.id)
  books.insert(Book("Project Hail Mary: A Novel"))
  val andyWeirsBooks = books.selectAll().map(books::toRecord)
}
```

## Contributing

To build locally:
```shell
.scripts/setup.sh && gradle clean test
```

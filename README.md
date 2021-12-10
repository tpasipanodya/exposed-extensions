# Exposed Extensions

A collection of extensions to [Exposed](https://github.com/tpasipanodya/Exposed). Includes:
- Logical tenant isolation, soft deletes and  (and more) via [Exposed](https://github.com/tpasipanodya/Exposed)'s default scopes.
- Declarative mapping of data classes/POKOs to and from the database. This is similar to
  [Exposed's](https://github.com/tpasipanodya/Exposed) DAO DSL with the exception that there is no magic. 
  You define how entities and their associations should be mapped and those rules can be applied for any
  query you write.
- Postgres array & jsonb column support

  
## How to Use
```kotlin
implementation("io.taff:exposed-extensions:0.3.0")
```

Using logical tenant isolation as an example:

```kotlin
/** 1. Declare your model. */
data class Book(
  var title: String? = null,
  var tenantId: Long? = null,
  override var id: Long? = null,
  override var createdAt: Instant? = null,
  override var updatedAt: Instant? = null
) : TenanatScopedModel<Long, Long>

/** 2. Declare how it is stored */
val books = object : TenantScopedLongIdTable<Long, Book>() {
  val title = varchar("name", 50).nullable()
  override val tenantId: Column<Long> = long("author_id")
  
  /** defines how to map your model from a select statement */
  override fun initializeModel(row: ResultRow) = Book(title = row[title])
  
  /** defines how to map your model to an insert/update statement */
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
  val zeeyaMeralisBooks = books.selectAll().map(books::toModel)

  setCurrentTenantId(andyWeir.id)
  books.insert(Book("Project Hail Mary: A Novel"))
  val andyWeirsBooks = books.selectAll().map(books::toModel)
}
```

[The Wiki ](WIKI.md) contains more documentation.

## Contributing

All Contributions are welcome. To build locally:
```shell
.scripts/setup.sh
```

## Declarative entity mapping

Everything revolves around the `Model<ID>` class and its derivatives.

| Class                        | Purpose                                                          |
| ---------------------------- | ---------------------------------------------------------------- |
| `Model<ID>`                  | Adds timestamp audit fields (`createdAt` and `updatedAt`)        |
| `SoftDeletableModel<ID>`     | Adds soft delete support via the `softDeletedAt` timestamp field |
| `TenantScopedModel<ID, TID>` | Adds tenant isolation support via the `tenantId` function        |

You only need to implement these if you plan on using model mapping DSL methods like `table.insert(myRecord)`.

## Postgres Array & JsonB Columns via The ModelMappingLongIdTable

```kotlin
data class Book(
    override var id: Long? = null,
    var pages: List<String> = listOf(),
    var sentenceCountsPerPage: Map<String, Int> = listOf(),
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Model<Long> { override fun tenantId() = authorId }

val books = object : ModelMappingLongIdTable<Book>() {
    val pages = stringArray("strings")
    val sentenceCountsPerPage = jsonB("sentence_counts_per_page")

    override fun initializeModel(row: ResultRow) = Book(
        pages = row[pages],
        sentenceCountsPerPage = row[sentenceCountsPerPage]
    )

    override fun appendStatementValues(stmt: UpdateBuilder<Int>, book: Book) {
        book.pages?.let { stmt[pages] = it }
        book.sentenceCountsPerPage.let { stmt[sentenceCountsPerPage] = it }
    }
}

books.insert(Book("Book 1", 
    listOf("lorem ipsum..."), 
    mapOf("1" to 300)))

val actualBooks = books.selectAll().map(books::toModel)

```

## Tenant Isolation

This is implemented using [Exposed's](https://github.com/tpasipanodya/Exposed) default scopes. To use, simply 
instantiate/extend your table from either `TenantScopedLongIdTable` or `TenantScopedUuidTable`.
`TenantScopedSoftDeletableLongIdTable` and `TenantScopedSoftDeletableUuidTable` also provide tenant isolation
with soft deletes. 

```kotlin
/** Given a table */
val books = object : TenantScopedLongIdTable<Model<Long>>() {
    val title = varchar("name", 50).nullable()
    override val tenantId: Column<Long> = long("author_id")

    /** assuming we won't be using the model mapping DSL with this table */
    override fun initializeModel(row: ResultRow) = object : Model<Long> { 
        override val id: Long = -1L 
    }
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: Model<long>) {}
}

transaction {
    /** executes the sql `SELECT * FROM books WHERE books.author_id IS NULL` */
    books.selectAll().toList()

    /** Sets a thread local variable and returns a coroutine context element */
    setCurrentTenantId(1L)
    /** executes the sql `SELECT * FROM books WHERE books.author_id = 1` */
    books.selectAll().toList()

    /** executes the sql `DELETE FROM books WHERE books.id IS NOT NULL AND books.author_id = 200` */
    setCurrentTenantId(200L)
    books.deleteWhere { books.id.isNotNull() }
    
    val previousTenantId = clearCurrentTenantId<Long>()

    /** executes the sql `DELETE FROM books WHERE books.id IS NOT NULL` */
    books.forAllTenants().deleteWhere { books.id.isNotNull() }
}

```
This will also apply to all SQL commands including updates and joins.

## Soft Deletes

This is implemented using [Exposed's](https://github.com/tpasipanodya/Exposed) default scopes. To use, simply
instantiate/extend your table from either `SoftDeletableLongIdTable` or `SoftDeletableUuidTable`.
`TenantScopedSoftDeletableLongIdTable` and `TenantScopedSoftDeletableUuidTable` also provides soft deletes
support with tenant isolation.


```kotlin
/** Given a table */
val books = object : SoftDeletableLongIdTable<Model<Long>>() {
    val title = varchar("name", 50).nullable()
        
    /** assuming we won't be using the model mapping DSL with this table */
    override fun initializeModel(row: ResultRow) = object : Model<Long> {
        override val id = -1L
    }
    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: Model<Long>) {}
}

transaction {
    /** executes the sql `SELECT * FROM books WHERE books.soft_deleted_at IS NULL` */
    books.selectAll().toList()

    /** executes the sql `SELECT * FROM books WHERE books.soft_deleted_at IS NOT NULL` */
    books.softDeleted().selectAll().toList()

    /** executes the sql `SELECT * FROM books` */
    books.liveAndSoftDeleted().selectAll().toList()
    
    /** You can also soft delete models in batches (assuming we had configured model mapping ofcourse) */
    val myBook = books.liveAndSoftDeleted().selectAll().first()
    books.softDelete(myBook)
}
```

For additional examples, take a look at this project's tests.

# Hephaestus

A collection of Kotlin tools for simplifying backend development. Currently, Includes the following:
- A declarative, functional & dynamic graphql client.
- Augmented [Exposed](https://github.com/JetBrains/Exposed) with tables that enable the following:
  - Declarative mapping KOJOs to and from A database via [Exposed](https://github.com/JetBrains/Exposed).
  - Tenant isolation
  - Soft deletes
  - Audit timestamp fields.

Soon to be added:
- [Kgraphql](https://kgraphql.io) augmented with the following:
  - JWT based authentication
  - Tenant Isolation (integrated into Exposed as well)
  
## Installing

implementation("io.taff:hephaestus:0.1.0")

## Using The Graphql Client

Given a graphql service hosted at `http://fancyservice.com/graphql` with the schema:
```graphql
type Author {
    name: String!
}

type Query {
    author(name: String!) : Author
}
```

You can use Hephaestus to query that service as shown below:
```kotlin
/** Create our model class */
data class Author(val name: String)

/** Create a client*/
val client = Client.new()
    .url("http://fancyservice.com/graphql")
    .build()

/** Call that service returning a deserialized Kotlin type */
val author = client.query("author") {
    input(name = "id", value = 1)
    select("name")
}.resultAs<Author>()
```
### What is it
A simple graphql query builder that lets you programmatically build and run graphql queries. For example, 
this can be very useful for testing graphql APIs without having to wrestle with Kotlin's type system. For an 
on how I did that, checkout this project's test suite.

### What is it not
A full fledged graphql client with built in type checking, validations, caching, and all the other bells and
whistles. This is simply just a dynamic query builder. For all that other stuff, productionized alternatives
like Apollo client exist.

As with all dynamically typed programming, queries and response parsing can fail at runtime so it's up to you,
the library user to test your queries, correctly parse responses and handle all possible network and graphql 
errors.

## Using The [Exposed](https://github.com/JetBrains/Exposed) Extensions

Everything is centered around a `Model<ID>`. A model is basically your data class representing an entity you
would like to store and retrieve from a database.

### Declarative Mapping of KOJOs To And Fom A Database

```kotlin
// 1. Declare your model.
data class Author(
  var name: String? = null, 
  override var id: Long? = null,
  override var createdAt: Instant? = null,
  override var updatedAt: Instant? = null
) : Model<Long>

// 2. Declare how it is stored
val authors = object : ModelMappingLongIdTable<Author>("authors") {
  val name = varchar("name", 50).nullable()
  override fun initializeModel(row: ResultRow) = Author(name = row[name])
  override fun appendStatementValues(stmt: UpdateBuilder<Int>, author: Author) {
    author.name?.let { stmt[name] = it }
  }
}

// 3. Create your table. e.g:
Database.connect(env<String>("DB_URL"))
transaction { SchemaUtils.create(authors) }

// 4. Query
val author = Author("Zeeya Merali")

transaction {
  val persistedAuthor = authors.insert(author).first()

  // true
  author == persistedAuthor

  // true
  authors.selectAll()
    .map(authors::toModel)
    .first()
    .let { reloadedAuthor -> persistedAuthor == reloasedAuthor }

  author.name = "Janna Levin"
  authors.update(author)
}
```

### Soft Deletes

```kotlin

// 1. Declare your model.
data class Author(
  var name: String? = null,
  override var id: UUID? = null,
  override var createdAt: Instant? = null,
  override var updatedAt: Instant? = null
) : DestroyableModel<UUID>

// 2. Declare how it is stored
val authors = object : DestroyableModelUuidTable<Author>("authors") {
  val name = varchar("name", 50).nullable()
  override fun initializeModel(row: ResultRow) = Author(name = row[name])
  override fun appendStatementValues(stmt: UpdateBuilder<Int>, author: Author) {
    author.name?.let { stmt[name] = it }
  }
}

// 3. Create your table:
Database.connect(env<String>("DB_URL"))
transaction { SchemaUtils.create(authors) }

// 4. Query
val author = Author("Zeeya Merali")

transaction {
  val persistedAuthor = authors.insert(author).first()
  val destroyedAuthor = authors.destroy(author).first()

  // false
  destroyedAuthor.destroyedAt.isNull()
}
```

### Tenant Isolation

```kotlin
// 1. Declare your model.
data class Author(
  var name: String? = null,
  override var tenantId: UUID? = null,
  override var id: UUID? = null,
  override var createdAt: Instant? = null,
  override var updatedAt: Instant? = null
) : TenanatScopedModel<UUID, UUID>

// 2. Declare how it is stored
val authors = object : TenantScopedUuidTable<UUID, Author>("authors") {
  val name = varchar("name", 50).nullable()
  override val tenantId: Column<UUID> = uuid("tenant_id")
  override fun initializeModel(row: ResultRow) = Author(name = row[name])
  override fun appendStatementValues(stmt: UpdateBuilder<Int>, author: Author) {
    author.name?.let { stmt[name] = it }
  }
}

// 3. Create your table:
Database.connect(env<String>("DB_URL"))
transaction { SchemaUtils.create(authors) }

// 4. Query
val author = Author("Zeeya Merali")

transaction {
  // Fails
  var persistedAuthor = authors.insert(author).first()

  // Succeeds
  setCurrentTenantId(myTenantId)
  persistedAuthor = authors.insert(author).first()

  // Fails
  setCurrentTenantId(otherTenantId)
  author.name = "Frank Herbert"
  val destroyedAuthor = authors.update(author).first()
}
```

### Tenant Isolation & Soft Deletes

To use both, your data class should implement `TenantScopedModel` and `DestroyableModel`. For tables, use
either `TenantScopedDestroyableLongIdTable` or `TenantScopedDestroyableUuidTable`.

### Postgres Columns

```kotlin
// 1. Declare your data class
data class MyModel(
    override var id: UUID? = null,
    var strings: List<String> = listOf(),
    var json: Map<String, Any> = mapOf(),
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Model<UUID>

// 2. Define how it will be stored
val myModels = object : ModelMappingUuidTable<MyModel>("my_models") {

    val strings = stringArray("strings")
    val json = jsonb("strings") {
      Config.objectMapper.readValue(
        it,
        object  : TypeReference<Map<String, Any>>(){}
      )
    }
    override fun initializeModel(row: ResultRow) = MyModel(
      strings = row[strings],
      json = row[json]
    )

    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: ModelWithMoment) {
      stmt[strings] = model.strings
      stmt[json] = model.json
    }
}

// 3. Create your table:
Database.connect(env<String>("DB_URL"))

// Query
Database.connect(env<String>("DB_URL"))
val author = MyModel(
  strings = listOf("foo", "bar"),
  json = mapOf("a" to mapOf("b" to "c"))
)

persisted = myModels.insert(author).first()
persisted.strings = listOf("lorem", "ipsum")
persisted.json = mapOf("foo" to listOf(1, 2, mapOf("lorem" to "ipsum")))
myModels.update(persisted)
```
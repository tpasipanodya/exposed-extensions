# Hephaestus

A collection of utilities for simplifying backend applications. Currently, only includes a dynamic graphql client.

## Download

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

/** Configure a client for the above service. */
val hephaestus = configure {
    graphqlClients.add(
        ClientConfig(
            "myFancyService",
            "http://fancyservice.com/graphql"
        )
    )
}

/** Call that service returning a deserialized Kotlin type */
val author = hephaestus
    .graphqlClients["myFancyService"]!!
    .query("author") {
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


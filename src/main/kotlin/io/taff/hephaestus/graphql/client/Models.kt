package io.taff.hephaestus.graphql.client

import com.fasterxml.jackson.core.type.TypeReference
import com.github.kittinunf.fuel.core.Response
import io.taff.hephaestus.Hephaestus
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * An input for a graphql operation.
 */
data class Input(val name: String,
                 val required: Boolean,
                 val value: Any,
                 val type: String = value.javaClass.simpleName) {

    fun coalescedValue() : Any = if (value is OffsetDateTime) {
        value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    } else value
}

/**
 * Configuration for setting up new graphql clients.
 */
data class ClientConfig(val name: Any, val url: String, val headers: Map<String, Any> = mapOf())


/**
 *
 */
class GraphqlResponse(val operationName: String, val _response: Response) {

    /**
     * Get a header.
     */
    fun header(key: String) = _response.header(key)

    /**
     * All headers.
     */
    val headers = _response.headers

    /**
     * The status code.
     */
    val statusCode = _response.statusCode

    /**
     * Get the raw body as a string.
     */
    fun bodyAsString(contentType: String) = _response.body().asString(contentType)
    
    /**
     * Parse the query/mutation's result to a map.
     */
    fun asMap() : Map<String, Any?> = parseBodyAsMap()
        .let { it[operationName] as Map<String, Any?> }

    /**
     * Parse the query/mutation's result to a list of maps.
     */
    fun asListOfMaps() : List<Map<String, Any?>> = parseBodyAsMap()
        .let { it[operationName] as List<Map<String, Any?>> }

    /**
     * Helper function for deserializing a query/mutation's result to a map.
     */
    private fun parseBodyAsMap() : Map<String, Any?> = _response.body()
        .asString("application/json")
        .let {
            Hephaestus.objectMapper.readValue(
                it,
                object : TypeReference<Map<String, Any?>>() {}
            )
        }.let { it["data"]!! as Map<String, Any?> }

    /**
     * Parse the query/mutation's result to an instance of type T.
     * @param T the return type.
     */
    inline fun <reified T> asType(contentType: String = "application/json") : T = _response.body()
        .asString(contentType)
        .let { body ->
            Hephaestus
                .objectMapper.readTree(body).let { parsedBody ->
                    parsedBody["data"]!![operationName]!!
                        .let {
                            Hephaestus
                                .objectMapper.readValue(
                                    it.toPrettyString(),
                                    object : TypeReference<T>() {}
                                )
                        }
                }

        }
}
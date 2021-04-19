package io.taff.hephaestus.graphql.client

import com.fasterxml.jackson.core.type.TypeReference
import com.github.kittinunf.fuel.core.Response
import io.taff.hephaestus.Config

/**
 * Represents the response from performing a graphql opeartion.
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
    fun bodyAsString(contentType: String = "application/json") = _response.body()
        .asString(contentType)

    /**
     *
     */
    fun errors(contentType: String = "application/json") = bodyAsString(contentType)
        .let {
            Config.objectMapper.readValue(
                it,
                object : TypeReference<Map<String, Any?>>() {}
            )
        }.let { it["errors"] as Map<String, List<String>?>? }

    /**
     * Parse the query/mutation's result to a map.
     */
    fun resultAsMap(contentType: String = "application/json") = parseDataAsMap(contentType)
        .let { it!![operationName] as Map<String, Any?>? }

    /**
     * Parse the query/mutation's result to a list of maps.
     */
    fun resultAsListOfMaps(contentType: String = "application/json") = parseDataAsMap(contentType)
        .let { it!![operationName] as List<Map<String, Any?>>? }

    /**
     * Helper function for deserializing a query/mutation's result to a map.
     */
    private fun parseDataAsMap(contentType: String = "application/json") = _response.body()
        .asString(contentType)
        .let {
            Config.objectMapper.readValue(
                it,
                object : TypeReference<Map<String, Any?>>() {}
            )
        }.let { it["data"] as Map<String, Any?>? }

    /**
     * Parse the query/mutation's result to an instance of type T.
     * @param T the return type.
     */
    inline fun <reified T> resultAs(contentType: String = "application/json") : T? = _response.body()
        .asString(contentType)
        .let { body ->
            Config
                .objectMapper.readTree(body).let { parsedBody ->
                    parsedBody["data"]!![operationName]?.let {
                        Config
                            .objectMapper.readValue(
                                it.toPrettyString(),
                                object : TypeReference<T>() {}
                            )
                    }
                }

        }
}
package io.taff.hephaestus.graphql.client

import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import io.taff.hephaestus.Config
import java.lang.IllegalStateException

/**
 * Access point for querying graphql services.
 */
class Client(private val config: ClientBuilder) {

    companion object {
        fun new() = ClientBuilder()
    }

    /**
     * Perform a graphql query.
     *
     * @param name The name of the query.
     * @param serviceName The service hosting the named mutation.
     * @param runtimeHeaders Additional headers to use for this specific call.
     * @param queryBuilder Lambda for building out the mutation's body
     *
     * e.g
     * ```
     * authHeaders =  mapOf("Authorization" to "Bearer xxx")
     * mutation("addNumbers", "numberAdder", authHeaders) {
     *  input("id", true, 1)
     *  select("id", "firstname", "email")
     * }
     * ```
     */
    fun query(name: String,
              runtimeHeaders: Map<String, Any> = mapOf(),
              queryBuilder: QueryMutationDSL.() -> Unit = {}) = operation(
        name = name,
        type = OperationType.QUERY,
        queryBuilder = queryBuilder,
        runtimeHeaders = runtimeHeaders,
    )

    /**
     * Perform a graphql mutation.
     *
     * @param name The name of the query.
     * @param serviceName The service hosting the named mutation.
     * @param runtimeHeaders Additional headers to use for this specific call.
     * @param queryBuilder Lambda for building out the mutation's body
     *
     * e.g
     * ```
     * authHeaders =  mapOf("Authorization" to "Bearer xxx")
     * mutation("addNumbers", "numberAdder", authHeaders) {
     *  input("id", true, 1)
     *  select("id", "firstname", "email")
     * }
     * ```
     */
    fun mutation(name: String,
                 runtimeHeaders: Map<String, Any> = mapOf(),
                 queryBuilder: QueryMutationDSL.() -> Unit = {}) = operation(
        name = name,
        type = OperationType.MUTATION,
        queryBuilder = queryBuilder,
        runtimeHeaders = runtimeHeaders,
    )

    /**
     * Make a graphql call.
     */
    private fun operation(name: String,
                           type: OperationType,
                           runtimeHeaders: Map<String, Any> = mapOf(),
                           queryBuilder: QueryMutationDSL.() -> Unit) = QueryMutationDSL(name, type)
        .apply(queryBuilder)
        .compile()
        .let { compiledQuery ->
            val headers = config.headers + runtimeHeaders
            if (Config.logGraphqlClientRequests) {
                Config.logger.info {
                    "\n\tquery: $compiledQuery ${
                    if (Config.logGraphqlClientRequestHeaders) "\n\theaders: $headers"
                    else ""
                }"
                }
            }
            config.url
                .httpPost()
                .body(compiledQuery)
                .header(headers)
                .responseString { _, response, result ->
                    when (result) {
                        is Result.Failure -> throw result.getException()

                        is Result.Success -> result.get()

                        else -> throw IllegalStateException("Unknown result $result")
                    }
                }.join()
                .let { GraphqlResponse(name, it) }
        }
}
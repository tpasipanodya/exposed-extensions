package io.taff.hephaestus

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.taff.hephaestus.graphql.client.ClientRegistry
import mu.NamedKLogging

object Hephaestus {

	/**
	 * Used for all logging.
	 */
	val logger = NamedKLogging("hephaestus").logger

	/**
	 * Whether graphql requests to other services should be logged.
	 */
	var logGraphqlClientRequests = false

	/**
	 * Whether or not graphql request headers to other services should be logged.
	 */
	var logGraphqlClientRequestHeaders = false

	/**
	 * Graphql clients.
	 * ```
	 * graphqlClients.add(ClientConfig("myFancyService", "http://fancyservice.com/graphql))
	 *
	 * graphqlClients["myFancyService"]?.query("...") {
	 *    ...
	 * }
	 * ```
	 */
	val graphqlClients = ClientRegistry

	/**
	 * used for serializing objects for logging as well as deserializing json into lists and maps.
	 */
	var objectMapper = jacksonObjectMapper().apply {
		configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
		registerModule(JavaTimeModule())
	}
}

/**
 * Configure Hephaestus, e.g:
 * ```
 * val configured = configure { logGraphqlClientRequests = true }
 * ```
 */
fun configure(fxn: Hephaestus.() -> Unit) = fxn(Hephaestus).let { Hephaestus }

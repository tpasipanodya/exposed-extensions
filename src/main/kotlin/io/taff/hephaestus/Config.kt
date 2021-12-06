package io.taff.hephaestus

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.NamedKLogging
import java.util.*

object Config {

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
fun configure(fxn: Config.() -> Unit) = fxn(Config).let { Config }

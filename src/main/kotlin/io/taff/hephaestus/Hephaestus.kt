package io.taff.hephaestus

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.httpPost
import io.taff.hephaestus.graphql.client.ServiceConfig
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import io.taff.hephaestus.graphql.client.ServiceRegistry
import mu.NamedKLogging
import java.lang.IllegalStateException


object Config {

	/**
	 * Used for all logging.
	 */
	val logger = NamedKLogging("hephaestus").logger

	/**
	 *
	 */
	val services = ServiceRegistry

	/**
	 * used for serializing objects for logging as well as deserializing json into lists and maps.
	 */
	var objectMapper = jacksonObjectMapper().apply {
		configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
		registerModule(JavaTimeModule())
	}
}

fun configure(fxn: Config.() -> Unit) = fxn(Config).let { Config }

package io.taff.hephaestus.graphql.client

object ServiceRegistry {

    private val services = mutableMapOf<Any, Service>()

    fun service(config: ServiceConfig) {
        services[config.name] = Service(config)
    }

    operator fun <N : Any> get(name: N) = services[name]
}
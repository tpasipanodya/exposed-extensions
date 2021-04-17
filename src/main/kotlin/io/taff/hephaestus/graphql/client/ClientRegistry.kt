package io.taff.hephaestus.graphql.client

/**
 * Stores graphql clients.
 */
object ClientRegistry {

    private val clients = mutableMapOf<Any, Client>()

    /**
     * Add a new client.
     * @param config The client configuration.
     */
    fun add(config: ClientConfig) {
        clients[config.name] = Client(config)
    }

    /**
     * Get a client by name.
     * @param N The type for the name.
     * @param name The name.
     */
    operator fun <N : Any> get(name: N) : Client? = clients[name]
}
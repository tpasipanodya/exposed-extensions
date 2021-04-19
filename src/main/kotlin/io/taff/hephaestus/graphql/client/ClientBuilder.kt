package io.taff.hephaestus.graphql.client

/**
 * Configuration for setting up new graphql clients.
 */
class ClientBuilder {

    lateinit var url: String

    var headers: MutableMap<String, Any> = mutableMapOf()

    /**
     * Set the Url.
     */
    fun url(arg: String) = this.also {
        url = arg
    }

    /**
     * Set headers. Overwrites any present headers on key collisions.
     */
    fun headers(vararg args: Pair<String, Any>) = this.also {
        args.forEach { header ->
            headers[header.first] = header.second
        }
    }

    /**
     * Build the client instance.
     */
    fun build() = Client(this)
}
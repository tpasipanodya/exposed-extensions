package io.taff.hephaestus.graphql.client

import io.taff.hephaestus.Config
import io.taff.hephaestus.graphql.client.selections.Selection


class QueryMutationDSL(var name: String, var type: OperationType) : Selection {

    var inputs: MutableSet<Input> = mutableSetOf()
    override val rawSelections: MutableList<Compilable<String>> = mutableListOf()

    /**
     * Add an input to the current operation being built
     *
     * @param name The name of the input in the graphql API.
     * @param required true when this input is required in the API, otherwise false. Defaults to true.
     * @param value The input's value.
     */
    fun input(name: String,
              required: Boolean = true,
              value: Any) = Input(name, required, value)
        .also { inputs.add(it) }

    /**
     * Add an iterable input to the current operation being built.
     *
     * @param E The iterable's element type.
     * @param name the name of the input in the graphql API
     * @param required true when this input is required in the API, otherwise false. Defaults to true.
     * @param value The input's value. An iterable of type E.
     */
    inline fun <reified E> input(name: String,
                                 required: Boolean = true,
                                 value: Iterable<E>,
                                 requiredElements: Boolean = true) = Input(
        name,
        required,
        value,
        type = "[${E::class.java.simpleName}" + if (requiredElements) "!]" else "" +"]").also { inputs.add(it) }

    /**
     * Compile this query/mutation.
     */
    override fun compile() = mapOf(
        "query" to compiledQuery(),
        "variables" to inputs.associateBy({ it.name }) {
            it.coalescedValue()
        }
    ).let { Config.objectMapper.writeValueAsString(it) }

    private fun compileSelections() = if (rawSelections.isEmpty()) "" else "{ ${super.compile()} }"

    private fun compileHeader() = if (inputs.isEmpty()) {
        type.compile()
    } else {
        "${type.compile()}(" +
                inputs.map {
                        input -> "\$${input.name}: ${input.type}${if (input.required) "!" else ""}"
                }.joinToString() + ")"
    }

    private fun compileBody() = """$name${
        if (inputs.isEmpty()) ""
        else "(" + inputs.map { (name) -> "$name: \$$name" }.joinToString { it } + ")"
    } ${compileSelections()}""".trimIndent()

    private fun compiledQuery() = "${compileHeader()} { " + compileBody() + " }"
}

package io.taff.hephaestus.graphql.client.selections

import io.taff.hephaestus.graphql.client.Compilable

/**
 * For selection associations.
 */
class AssociationSelection(private val field: String, selector: AssociationSelection.() -> Unit = {}) : Selection {

    override val rawSelections: MutableList<Compilable<String>> = mutableListOf()

    init { selector() }

    override fun compile() = "$field${
        if (rawSelections.isNotEmpty())
            " {\n\t${rawSelections.map {
                it.compile()
            }.joinToString(separator = "\n\t")}}"
        else
            ""
    }"
}
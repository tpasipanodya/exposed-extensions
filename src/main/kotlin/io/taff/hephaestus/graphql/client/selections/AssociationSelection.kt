package io.taff.hephaestus.graphql.client.selections

import io.taff.hephaestus.graphql.client.Compilable

/**
 * For selecting associations.
 */
class AssociationSelection(private val field: String, selector: AssociationSelection.() -> Unit = {}) : Selection {

    override val rawSelections: MutableList<Compilable<String>> = mutableListOf()

    init { selector() }

    override fun compile() = "$field${
        if (rawSelections.isNotEmpty())
            " { ${rawSelections.joinToString(separator = " ") { it.compile() }} }"
        else
            ""
    }"
}
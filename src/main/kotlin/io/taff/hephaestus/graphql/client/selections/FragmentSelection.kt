package io.taff.hephaestus.graphql.client.selections

import io.taff.hephaestus.graphql.client.Compilable


/**
 * For selecting fragments.
 */
class FragmentSelection(private val entity: String, selector: FragmentSelection.() -> Unit = {}) : Selection {

    override var rawSelections: MutableList<Compilable<String>> = mutableListOf()

    init { selector() }

    override fun compile() = "...on $entity { ${
        rawSelections.joinToString(separator = " ") { it.compile() }
    } }"
}

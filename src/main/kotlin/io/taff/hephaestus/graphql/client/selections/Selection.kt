package io.taff.hephaestus.graphql.client.selections

import io.taff.hephaestus.graphql.client.Compilable

/**
 * For arbitrarily selecting things on queries, mutations, associations and fragments.
 */
interface Selection : Compilable<String> {

    val rawSelections: MutableList<Compilable<String>>

    /**
     * Select the named attributes. Only flat attributes can be selected this way, so this won't work on
     * fragments or associations.
     */
    fun select(vararg fields: String) = rawSelections.addAll(fields.map(::Node))

    /**
     * Select attributes on an association.
     */
    fun onAssociation(field: String,
                      selector: AssociationSelection.() -> Unit) = rawSelections.add(AssociationSelection(field, selector))

    /**
     * Select attributes on a fragment.
     */
    fun onFragment(entity: String,
                   selector: (FragmentSelection.() -> Unit)) = rawSelections.add(FragmentSelection(entity, selector))

    /**
     * Compile these selections into their string form.
     */
    override fun compile() = rawSelections.map {
        it.compile()
    }.joinToString(separator = " ")
}
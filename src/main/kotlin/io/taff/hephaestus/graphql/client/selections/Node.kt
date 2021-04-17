package io.taff.hephaestus.graphql.client.selections

import io.taff.hephaestus.graphql.client.Compilable

/**
 * Abstraction around selections.
 */
data class Node(val value: String) : Compilable<String> {
    override fun compile() = value
}
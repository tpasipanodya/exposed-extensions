package io.taff.exposed.extensions.records

import java.time.Instant
import io.taff.exposed.extensions.isNull

/**
 * Records that have an Id.
 *
 * @param ID the id type.
 */
interface Record<ID : Comparable<ID>> {

    var id: ID?
    var createdAt: Instant?
    var updatedAt: Instant?

    fun isPersisted() = !id.isNull()
}


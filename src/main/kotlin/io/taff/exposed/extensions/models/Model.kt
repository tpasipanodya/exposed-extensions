package io.taff.exposed.extensions.models

import java.time.Instant
import io.taff.exposed.extensions.helpers.isNull

/**
 * Models that have an Id.
 *
 * @param ID the id type.
 */
interface Model<ID : Comparable<ID>> {

    var id: ID?
    var createdAt: Instant?
    var updatedAt: Instant?

    fun isPersisted() = !id.isNull()
}


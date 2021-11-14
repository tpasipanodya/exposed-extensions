package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.helpers.isNull
import java.time.Instant

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


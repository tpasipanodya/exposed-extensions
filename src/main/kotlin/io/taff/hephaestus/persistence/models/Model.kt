package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.helpers.isNull
import java.time.OffsetDateTime

/**
 * Models that have an Id.
 *
 * @param ID the id type.
 */
interface Model<ID : Comparable<ID>> {

    var id: ID?
    var createdAt: OffsetDateTime?
    var updatedAt: OffsetDateTime?

    fun isPersisted() = !id.isNull()
}


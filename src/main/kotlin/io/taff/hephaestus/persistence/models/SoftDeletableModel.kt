package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.helpers.isNull
import java.time.Instant

/**
 * Models that can be soft deleted.
 *
 * @param ID The id type.
 */
interface SoftDeletableModel<ID : Comparable<ID>> : Model<ID> {

    var softDeletedAt: Instant?

    fun markAsSoftDeleted() {
        softDeletedAt = Instant.now()
    }

    fun markAsLive() {
        softDeletedAt = null
    }

    fun isSoftDeleted() = !softDeletedAt.isNull()
}

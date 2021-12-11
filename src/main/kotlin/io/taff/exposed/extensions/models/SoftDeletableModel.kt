package io.taff.exposed.extensions.models

import io.taff.exposed.extensions.helpers.isNull
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

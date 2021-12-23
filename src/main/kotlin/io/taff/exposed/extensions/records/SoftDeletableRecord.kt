package io.taff.exposed.extensions.records

import io.taff.exposed.extensions.isNull
import java.time.Instant

/**
 * Records that can be soft deleted.
 *
 * @param ID The id type.
 */
interface SoftDeletableRecord<ID : Comparable<ID>> : Record<ID> {

    var softDeletedAt: Instant?

    fun markAsSoftDeleted() {
        softDeletedAt = Instant.now()
    }

    fun markAsLive() {
        softDeletedAt = null
    }

    fun isSoftDeleted() = !softDeletedAt.isNull()
}

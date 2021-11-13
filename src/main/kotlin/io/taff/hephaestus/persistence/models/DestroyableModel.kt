package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.helpers.isNull
import java.time.OffsetDateTime

/**
 * Models that can be soft deleted.
 *
 * @param ID The id type.
 */
interface DestroyableModel<ID : Comparable<ID>> : Model<ID> {

    var destroyedAt: OffsetDateTime?

    fun markAsDestroyed() {
        destroyedAt = OffsetDateTime.now()
    }

    fun isDestroyed() = !destroyedAt.isNull()
}
package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.helpers.isNull
import java.time.Instant

/**
 * Models that can be soft deleted.
 *
 * @param ID The id type.
 */
interface DestroyableModel<ID : Comparable<ID>> : Model<ID> {

    var destroyedAt: Instant?

    fun markAsDestroyed() {
        destroyedAt = Instant.now()
    }

    fun isDestroyed() = !destroyedAt.isNull()
}

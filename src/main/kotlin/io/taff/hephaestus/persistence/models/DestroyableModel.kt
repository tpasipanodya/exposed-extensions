package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.helpers.isNull
import java.time.OffsetDateTime

interface DestroyableModel : Model {

    var destroyedAt: OffsetDateTime?

    fun markAsDestroyed() {
        destroyedAt = OffsetDateTime.now()
    }

    fun isDestroyed() = !destroyedAt.isNull()
}
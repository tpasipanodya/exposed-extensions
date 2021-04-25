package io.taff.hephaestus.persistence.models

import java.time.OffsetDateTime

interface DestroyableModel : Model {

    var destroyedAt: OffsetDateTime?

    fun markAsDestroyed() {
        destroyedAt = OffsetDateTime.now()
    }
}
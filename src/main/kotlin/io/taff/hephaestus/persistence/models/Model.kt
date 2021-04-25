package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.helpers.isNull
import java.time.OffsetDateTime

interface Model {

    var id: Long?
    var createdAt: OffsetDateTime?
    var updatedAt: OffsetDateTime?

    fun isPersisted() = !id.isNull()
}


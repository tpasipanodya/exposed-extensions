package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.helpers.isNull
import java.time.OffsetDateTime
import java.util.*

interface Model {

    var id: UUID?
    var createdAt: OffsetDateTime?
    var updatedAt: OffsetDateTime?

    fun isPersisted() = !id.isNull()
}


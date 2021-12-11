package io.taff.exposed.extensions.models

import io.taff.exposed.extensions.models.examples.includeModelSpeks
import org.spekframework.spek2.Spek
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

data class MyModel(
    override var id: UUID?,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Model<UUID>

object ModelSpek : Spek({

    includeModelSpeks(UUID.randomUUID()) { MyModel(id = it) }
})

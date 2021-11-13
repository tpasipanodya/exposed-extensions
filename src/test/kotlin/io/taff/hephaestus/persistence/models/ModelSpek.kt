package io.taff.hephaestus.persistence.models

import org.spekframework.spek2.Spek
import java.time.OffsetDateTime
import java.util.*

data class MyModel(
    override var id: UUID?,
    override var createdAt: OffsetDateTime? = null,
    override var updatedAt: OffsetDateTime? = null
) : Model<UUID>

object ModelSpek : Spek({

    includeModelSpeks { MyModel(id = it) }

})
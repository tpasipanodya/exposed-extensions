package io.taff.exposed.extensions.records

import io.taff.exposed.extensions.records.examples.includeRecordSpeks
import org.spekframework.spek2.Spek
import java.time.Instant
import java.util.*

data class MyRecord(
    override var id: UUID?,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Record<UUID>

object RecordSpek : Spek({

    includeRecordSpeks(UUID.randomUUID()) { MyRecord(id = it) }
})

package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.records.Record
import io.taff.exposed.extensions.tables.traits.RecordMappingTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now
import java.util.*

/**
 * A table that provides basic record mapping functionality.
 * @param M The concrete record type.
 */
abstract class RecordMappingUuidTable<M : Record<UUID>>(name: String)
    : UUIDTable(name), RecordMappingTableTrait<UUID, M, RecordMappingUuidTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }

    override fun self() = this
}


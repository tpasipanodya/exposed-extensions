package io.taff.exposed.extensions.tables.long

import io.taff.exposed.extensions.records.Record
import io.taff.exposed.extensions.tables.traits.RecordMappingTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now

/**
 * A table that provides basic record mapping functionality.
 * @param M The concrete record type.
 */
abstract class RecordMappingLongIdTable<M : Record<Long>>(name: String)
    : LongIdTable(name), RecordMappingTableTrait<Long, M, RecordMappingLongIdTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }

    override fun self() = this
}


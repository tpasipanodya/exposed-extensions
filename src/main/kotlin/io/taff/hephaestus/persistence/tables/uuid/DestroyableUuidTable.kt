package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now
import java.util.*

/**
 * A table that supports soft-deletes by setting the `destroyed_at` column on destruction.
 * @param M The concrete model type.
 */
abstract class DestroyableUuidTable<M : DestroyableModel<UUID>>(val name: String)
    : UUIDTable(name), DestroyableTableTrait<UUID, M, DestroyableUuidTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val destroyedAt = timestamp("destroyed_at").nullable()

    override fun self() = this
}

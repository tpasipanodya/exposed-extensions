package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.*

/**
 * A table that supports soft-deletes by setting the `destroyed_at` column on destruction.
 * @param M The concrete model type.
 */
abstract class DestroyableUuidTable<M : DestroyableModel<UUID>>(val name: String)
    : UUIDTable(name), DestroyableTableTrait<UUID, M, DestroyableUuidTable<M>> {

    override val createdAt = moment("created_at").clientDefault { OffsetDateTime.now() }
    override val updatedAt = moment("updated_at").clientDefault { OffsetDateTime.now() }
    override val destroyedAt = moment("destroyed_at").nullable()

    override fun self() = this
}
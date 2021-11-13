package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableModelTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.*

/**
 * A table that supports soft-deletes by setting the `destroyed_at` column on destruction.
 * @param M The concrete model type.
 */
abstract class DestroyableModelTable<M : DestroyableModel<UUID>>(val name: String)
    : UUIDTable(name), DestroyableModelTableTrait<UUID, M, DestroyableModelTable<M>> {

    override val createdAt = moment("created_at").clientDefault { OffsetDateTime.now() }
    override val updatedAt = moment("updated_at").clientDefault { OffsetDateTime.now() }
    override val destroyedAt = moment("destroyed_at").nullable()

    override fun self() = this
}
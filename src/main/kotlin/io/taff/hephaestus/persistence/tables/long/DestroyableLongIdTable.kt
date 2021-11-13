package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import java.time.OffsetDateTime

/**
 * A table that supports soft-deletes by setting the `destroyed_at` column on destruction.
 * @param M The concrete model type.
 */
abstract class DestroyableModelLongTable<M : DestroyableModel<Long>>(val name: String)
    : LongIdTable(name), DestroyableTableTrait<Long, M, DestroyableModelLongTable<M>> {

    override val createdAt = moment("created_at").clientDefault { OffsetDateTime.now() }
    override val updatedAt = moment("updated_at").clientDefault { OffsetDateTime.now() }
    override val destroyedAt = moment("destroyed_at").nullable()

    override fun self() = this
}
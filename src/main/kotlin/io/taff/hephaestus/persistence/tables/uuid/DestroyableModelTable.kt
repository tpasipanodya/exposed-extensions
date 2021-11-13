package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableModelTableTrait
import io.taff.hephaestus.persistence.tables.traits.MappingTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime

/**
 * A table that supports soft-deletes by setting the `destroyed_at` column on destruction.
 */
abstract class DestroyableModelTable<M : DestroyableModel>(val name: String)
    : UUIDTable(name), DestroyableModelTableTrait<M, DestroyableModelTable<M>> {

    override val createdAt = moment("created_at").clientDefault { OffsetDateTime.now() }
    override val updatedAt = moment("updated_at").clientDefault { OffsetDateTime.now() }
    override val destroyedAt = moment("destroyed_at").nullable()

    override fun self() = this
}
package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * A table that supports soft-deletes by setting the `destroyed_at` column on destruction.
 * @param M The concrete model type.
 */
abstract class DestroyableModelLongIdTable<M : DestroyableModel<Long>>(val name: String)
    : LongIdTable(name), DestroyableTableTrait<Long, M, DestroyableModelLongIdTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
    override val destroyedAt = timestamp("destroyed_at").nullable()

    override fun self() = this
}

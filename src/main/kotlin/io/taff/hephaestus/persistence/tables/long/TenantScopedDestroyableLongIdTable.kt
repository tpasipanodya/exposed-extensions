package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.traits.TenantScopedDestroyableTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now

/**
 * A table that enforces tenant isolation unless explicitly requested not to.
 * Also supports soft deletes by setting the`destroyed_at` column on destruction.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 */
abstract class TenantScopedDestroyableLongIdTable<TID : Comparable<TID>, M>(name: String, )
    : LongIdTable(name),
    TenantScopedDestroyableTableTrait<Long, TID, M, TenantScopedDestroyableLongIdTable<TID, M>>
        where M : TenantScopedModel<Long, TID>, M : DestroyableModel<Long> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val destroyedAt = timestamp("destroyed_at").nullable()

    override fun self() = this
}

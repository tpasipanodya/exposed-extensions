package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.traits.TenantScopedDestroyableTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now
import java.util.*

/**
 * A table that enforces tenant isolation unless explicitly requested not to.
 * Also supports soft deletes by setting the`destroyed_at` column on destruction.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 */
abstract class TenantScopedDestroyableUuidTable<TID : Comparable<TID>, M>(val name: String, )
    : UUIDTable(name),
    TenantScopedDestroyableTableTrait<UUID, TID, M, TenantScopedDestroyableUuidTable<TID, M>>
        where M : TenantScopedModel<UUID, TID>, M :  DestroyableModel<UUID> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val destroyedAt = timestamp("destroyed_at").nullable()

    override fun self() = this
}

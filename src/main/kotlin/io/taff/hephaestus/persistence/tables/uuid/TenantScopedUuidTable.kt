package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.traits.TenantScopedTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now
import java.util.*

/**
 * A table that enforces tenant isolation unless requested not to.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 */
abstract class TenantScopedUuidTable<TID : Comparable<TID>, M : TenantScopedModel<UUID, TID>>(val name: String)
    :UUIDTable(name = name), TenantScopedTableTrait<UUID, TID, M, TenantScopedUuidTable<TID, M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }

    override fun self() = this
}

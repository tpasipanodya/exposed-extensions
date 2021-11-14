package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.tables.traits.TenantScopedTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now

/**
 * A table that enforces tenant isolation unless requested not to.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 */
abstract class TenantScopedLongIdTable<TID : Comparable<TID>, M : TenantScopedModel<Long, TID>>(val name: String)
    :LongIdTable(name = name), TenantScopedTableTrait<Long, TID, M, TenantScopedLongIdTable<TID, M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }

    override fun self() = this
}

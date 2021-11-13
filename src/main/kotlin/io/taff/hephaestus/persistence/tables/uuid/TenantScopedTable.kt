package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.tables.traits.TenantScopedTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.*

/**
 * A table that enforces tenant isolation unless requested not to.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 */
abstract class TenantScopedTable<TID : Comparable<TID>, M : TenantScopedModel<UUID, TID>>(val name: String)
    :UUIDTable(name = name), TenantScopedTableTrait<UUID, TID, M, TenantScopedTable<TID, M>> {

    override val createdAt = moment("created_at").clientDefault { OffsetDateTime.now() }
    override val updatedAt = moment("updated_at").clientDefault { OffsetDateTime.now() }

    override fun self() = this
}
package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.tables.traits.TenantScopedTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime
import java.util.*

/**
 * A table that provides tenant isolation via query methods prefixed with `scoped`.
 */
abstract class TenantScopedTable<M : TenantScopedModel>(
    val name: String,
    tenantIdColumnName: String = "tenant_id"
) : UUIDTable(name = name), TenantScopedTableTrait<M, TenantScopedTable<M>> {

    override val tenantId: Column<UUID> = uuid(tenantIdColumnName)
    override val createdAt = moment("created_at").clientDefault { OffsetDateTime.now() }
    override val updatedAt = moment("updated_at").clientDefault { OffsetDateTime.now() }

    override fun self() = this
}
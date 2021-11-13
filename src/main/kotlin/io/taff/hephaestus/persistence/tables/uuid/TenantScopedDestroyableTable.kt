package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.tables.traits.DestroyableModelTableTrait
import io.taff.hephaestus.persistence.tables.traits.TenantScopedDestroyableModelTableTrait
import io.taff.hephaestus.persistence.tables.traits.TenantScopedTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime
import java.util.*

/**
 * A table that provides tenant isolation via query methods prefixed with `scoped`. Also supports soft deletes by setting the
 * `destroyed_at` column on destruction
 */
abstract class TenantScopedDestroyableTable<M>(
    val name: String,
    tenantIdColumnName: String = "tenant_id"
) : UUIDTable(name),
    TenantScopedDestroyableModelTableTrait<M, TenantScopedDestroyableTable<M>>
        where M : TenantScopedModel, M :  DestroyableModel {

    override val tenantId: Column<UUID> = uuid(tenantIdColumnName)
    override val createdAt = moment("created_at").clientDefault { OffsetDateTime.now() }
    override val updatedAt = moment("updated_at").clientDefault { OffsetDateTime.now() }
    override val destroyedAt = moment("destroyed_at").nullable()

    override fun self() = this
}
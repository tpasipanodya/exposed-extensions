package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.models.TenantScopedModel
import io.taff.exposed.extensions.tables.traits.TenantScopedTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now
import java.util.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * A table that enforces tenant isolation unless requested not to.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 */
abstract class TenantScopedUuidTable<TID : Comparable<TID>, M : TenantScopedModel<UUID, TID>>(name: String)
    :UUIDTable(name = name), TenantScopedTableTrait<UUID, TID, M, TenantScopedUuidTable<TID, M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val defaultScope: (()-> Op<Boolean>)? = { Op.build { currentTenantScope() } }

    override fun self() = this

    /** Returns a view on all records across tenants. Identical to stripDefaultScope */
    override fun forAllTenants() = AllTenantsView(this)

    class AllTenantsView<TID : Comparable<TID>, M : TenantScopedModel<UUID, TID>>(private val actual: TenantScopedUuidTable<TID, M>)
        :TenantScopedUuidTable<TID, M>(name = actual.tableName), TenantScopedTableTrait<UUID, TID, M, TenantScopedUuidTable<TID, M>> {

        override val columns = actual.columns
        override val tenantId: Column<TID> = actual.tenantId
        override val createdAt = actual.createdAt
        override val updatedAt = actual.updatedAt
        override val defaultScope:  (() -> Op<Boolean>)? = null

        override fun self() = this

        override fun initializeModel(row: ResultRow) = actual.initializeModel(row)

        override fun validateDestruction(models: Array<out M>) = models

        override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M, vararg skip: Column<*>) {
            super<TenantScopedUuidTable>.appendBaseStatementValues(stmt, model, tenantId, *skip)
        }

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M) =
            actual.appendStatementValues(stmt, model)
    }
}

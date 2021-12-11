package io.taff.exposed.extensions.tables.long

import io.taff.exposed.extensions.models.TenantScopedModel
import io.taff.exposed.extensions.tables.traits.TenantScopedTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now
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
abstract class TenantScopedLongIdTable<TID : Comparable<TID>, M : TenantScopedModel<Long, TID>>(name: String)
    :LongIdTable(name = name), TenantScopedTableTrait<Long, TID, M, TenantScopedLongIdTable<TID, M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val defaultScope: (() -> Op<Boolean>)? = { Op.build { currentTenantScope() } }
    override fun self() = this

    override fun forAllTenants() = AllTenantsView(this)

    class AllTenantsView<TID : Comparable<TID>, M : TenantScopedModel<Long, TID>>(private val actual: TenantScopedLongIdTable<TID, M>)
        : TenantScopedLongIdTable<TID, M>(name = actual.tableName), TenantScopedTableTrait<Long, TID, M, TenantScopedLongIdTable<TID, M>> {

        override val columns = actual.columns
        override val tenantId: Column<TID> = actual.tenantId
        override val createdAt = actual.createdAt
        override val updatedAt = actual.updatedAt
        override val defaultScope:  (() -> Op<Boolean>)? = null

        override fun self() = this

        override fun initializeModel(row: ResultRow) = actual.initializeModel(row)

        override fun validateDestruction(models: Array<out M>) = models

        override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M, vararg skip: Column<*>) {
            super<TenantScopedLongIdTable>.appendBaseStatementValues(stmt, model, tenantId, *skip)
        }

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M) =
            actual.appendStatementValues(stmt, model)
    }

}

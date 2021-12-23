package io.taff.exposed.extensions.tables.long

import io.taff.exposed.extensions.records.TenantScopedRecord
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
 * @param M The concrete record type.
 */
abstract class TenantScopedLongIdTable<TID : Comparable<TID>, M : TenantScopedRecord<Long, TID>>(name: String)
    :LongIdTable(name = name), TenantScopedTableTrait<Long, TID, M, TenantScopedLongIdTable<TID, M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val defaultScope: (() -> Op<Boolean>)? = { Op.build { currentTenantScope() } }
    override fun self() = this

    override fun forAllTenants() = AllTenantsView(this)

    class AllTenantsView<TID : Comparable<TID>, M : TenantScopedRecord<Long, TID>>(private val actual: TenantScopedLongIdTable<TID, M>)
        : TenantScopedLongIdTable<TID, M>(name = actual.tableName), TenantScopedTableTrait<Long, TID, M, TenantScopedLongIdTable<TID, M>> {

        override val columns = actual.columns
        override val tenantId: Column<TID> = actual.tenantId
        override val createdAt = actual.createdAt
        override val updatedAt = actual.updatedAt
        override val defaultScope:  (() -> Op<Boolean>)? = null

        override fun self() = this

        override fun initializeRecord(row: ResultRow) = actual.initializeRecord(row)

        override fun validateDestruction(records: Array<out M>) = records

        override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, record: M, vararg skip: Column<*>) {
            super<TenantScopedLongIdTable>.appendBaseStatementValues(stmt, record, tenantId, *skip)
        }

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: M) =
            actual.appendStatementValues(stmt, record)
    }

}

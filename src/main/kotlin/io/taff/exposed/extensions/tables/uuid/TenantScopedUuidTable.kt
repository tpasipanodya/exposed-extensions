package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.records.TenantScopedRecord
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
 * @param M The concrete record type.
 */
abstract class TenantScopedUuidTable<TID : Comparable<TID>, M : TenantScopedRecord<UUID, TID>>(name: String)
    :UUIDTable(name = name), TenantScopedTableTrait<UUID, TID, M, TenantScopedUuidTable<TID, M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val defaultFilter: (()-> Op<Boolean>)? = { Op.build { currentTenantScope() } }

    override fun self() = this

    /** Returns a view on all records across tenants. Identical to stripDefaultFilter */
    override fun forAllTenants() = AllTenantsView(this)

    class AllTenantsView<TID : Comparable<TID>, M : TenantScopedRecord<UUID, TID>>(private val actual: TenantScopedUuidTable<TID, M>)
        :TenantScopedUuidTable<TID, M>(name = actual.tableName), TenantScopedTableTrait<UUID, TID, M, TenantScopedUuidTable<TID, M>> {

        override val columns = actual.columns
        override val tenantId: Column<TID> = actual.tenantId
        override val createdAt = actual.createdAt
        override val updatedAt = actual.updatedAt
        override val defaultFilter:  (() -> Op<Boolean>)? = null

        override fun self() = this

        override fun initializeRecord(row: ResultRow) = actual.initializeRecord(row)

        override fun validateDestruction(records: Array<out M>) = records

        override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, record: M, vararg skip: Column<*>) {
            super<TenantScopedUuidTable>.appendBaseStatementValues(stmt, record, tenantId, *skip)
        }

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: M) =
            actual.appendStatementValues(stmt, record)
    }
}

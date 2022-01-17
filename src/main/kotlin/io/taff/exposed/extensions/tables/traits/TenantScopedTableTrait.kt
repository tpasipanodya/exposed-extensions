package io.taff.exposed.extensions.tables.traits

import io.taff.exposed.extensions.CurrentTenantId
import io.taff.exposed.extensions.TenantError
import io.taff.exposed.extensions.isNull
import io.taff.exposed.extensions.records.TenantScopedRecord
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * Tenant Isolation behavior applicable to tables.
 *
 * @param ID The record's concrete id type.
 * @param TID The concrete tenantId type.
 * @param M The record's concrete type.
 * @param T The underlying exposed table's concrete type.
 */
interface TenantScopedTableTrait<ID : Comparable<ID>, TID: Comparable<TID>, M : TenantScopedRecord<ID, TID>,
        T : IdTable<ID>> : RecordMappingTableTrait<ID, M, T> {

    /** The Tenant id column. */
    val tenantId: Column<TID>

    /** Returns a view on all records across tenants. Identical to stripDefaultFilter */
    fun forAllTenants() : T

    /** Set tenant Id on records loaded frm the DB */
    override fun toRecord(row: ResultRow) = super.toRecord(row)
        .also { it.tenantId = row[tenantId] }


    /** Set tenant Id on insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, record: M, vararg skip: Column<*>) {
        if (tenantId !in skip) {
            (CurrentTenantId.get() as TID?)
                ?.let { safeTenantId ->
                    if (record.tenantId.isNull() || record.tenantId == safeTenantId) {
                        stmt[tenantId] = safeTenantId
                        record.tenantId = safeTenantId
                    } else throw TenantError("Record ${record.id} can't be persisted because it doesn't belong to the current tenant.")
                } ?: throw TenantError("Record ${record.id} can't be persisted because There's no current tenant Id set.")
        }
        super.appendBaseStatementValues(stmt, record, *skip)
    }

    /** Hard delete the provided records and raise a TenantError if they don't belong to the current tenant */
    override fun delete(vararg records: M) = validateDestruction(records)
        .let { super.delete(*records) }

    /** Where clause for tenant isolation */
    fun SqlExpressionBuilder.currentTenantScope() = (CurrentTenantId.get() as TID?)
        ?.let { safeTenantId -> tenantId eq safeTenantId }
        ?: tenantId.isNull()

    /** verify that a delete/softDelete won't violate tenant isolation */
    fun validateDestruction(records: Array<out M>) = records.also {
        if (CurrentTenantId.get().isNull()) throw TenantError("Cannot delete records because there is no CurrentTenantId.")

        records.any { record -> record.tenantId != CurrentTenantId.get() }
            .also { belongsToOtherTenant ->
                if (belongsToOtherTenant) throw TenantError("Cannot delete records because they belong to a different tenant.")
            }
    }
}

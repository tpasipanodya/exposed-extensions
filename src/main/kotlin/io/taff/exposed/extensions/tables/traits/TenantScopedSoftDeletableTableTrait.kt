package io.taff.exposed.extensions.tables.traits

import io.taff.exposed.extensions.records.SoftDeletableRecord
import io.taff.exposed.extensions.records.TenantScopedRecord
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * Tenant Isolation + soft delete behavior applicable to tables.
 *
 * @param ID The record's concrete id type.
 * @param TID The concrete tenantId type.
 * @param M The concrete record type.
 * @param T The underlying exposed table's concrete type.
 */
interface TenantScopedSoftDeletableTableTrait<ID : Comparable<ID>, TID: Comparable<TID>, M, T>
    :TenantScopedTableTrait<ID, TID, M, T>, SoftDeletableTableTrait<ID, M, T>
        where M : TenantScopedRecord<ID, TID>,
              M : SoftDeletableRecord<ID>,
              T : IdTable<ID>,
              T:  SoftDeletableTableTrait<ID, M, T> {

    override fun forAllTenants() = liveForAllTenants()

    /**
     * Returns a version of this table that's scoped to soft deleted entities for all tenants.
     *
     * i.e negates the soft delete scope and strips the current tenant scope.
     */
    fun softDeletedForAllTenants() : T

    /**
     * Returns a version of this table that's scoped to both live and soft deleted records for all tenants.
     *
     * i.e strips all scopes.
     */
    fun liveAndSoftDeletedForAllTenants() : T

    /**
     * returns a version of this table that's scoped to live records for all tenants.
     *
     * i.e strips the soft delete scope and leaves the current tenant scope intact.
     *
     */
    fun liveForAllTenants() : T

    /** populate the record from a result row */
    override fun toRecord(row: ResultRow) = super<TenantScopedTableTrait>
        .toRecord(row)
        .also { it.softDeletedAt = row[softDeletedAt] }

    /** populate insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, record: M, vararg skip: Column<*>) {
        super<TenantScopedTableTrait>.appendBaseStatementValues(stmt, record, *skip)
        super<SoftDeletableTableTrait>.appendBaseStatementValues(stmt, record, *skip)
    }

    override fun delete(vararg records: M) : Boolean = super<TenantScopedTableTrait>.delete(*records)
        .also { recordIsDestroyed -> if (recordIsDestroyed) records.forEach { it.markAsSoftDeleted() } }


    override fun softDelete(vararg records: M) =  validateDestruction(records)
        .onEach(SoftDeletableRecord<ID>::markAsSoftDeleted)
        .let(::update)
        .also { recordIsSoftDeleted -> if (!recordIsSoftDeleted) records.forEach(SoftDeletableRecord<ID>::markAsLive) }
}

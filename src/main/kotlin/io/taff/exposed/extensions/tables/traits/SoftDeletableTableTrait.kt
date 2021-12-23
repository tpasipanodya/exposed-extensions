package io.taff.exposed.extensions.tables.traits

import io.taff.exposed.extensions.records.SoftDeletableRecord
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant

/**
 * Soft delete behavior applicable to tables.
 *
 * @param ID The record id type.
 * @param M The record.
 * @param T The underlying exposed table concrete type.
 */
interface SoftDeletableTableTrait<ID : Comparable<ID>, M : SoftDeletableRecord<ID>, T>
    : RecordMappingTableTrait<ID, M, T> where T : IdTable<ID>, T : SoftDeletableTableTrait<ID, M, T>  {

    val softDeletedAt: Column<Instant?>

    /**
     * Returns a copy of this table scoped to soft deleted records only.
     *
     * i.e negates the soft delete scope.
     */
    fun softDeleted() : T

    /**
     * Returns a copy of this table scoped to both live and soft deleted records.
     *
     * i.e strips the soft delete scope.
     */
    fun liveAndSoftDeleted() : T

    /** populate the insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, record: M, vararg skip: Column<*>) {
        if (self().softDeletedAt !in skip) {
            record.softDeletedAt?.let { stmt[softDeletedAt] = it }
        }
        super.appendBaseStatementValues(stmt, record, *skip)
    }

    /** Set tenant Id on records loaded frm the DB */
    override fun toRecord(row: ResultRow) = super.toRecord(row)
        .also { it.softDeletedAt = row[softDeletedAt] }

    /** Hard delete */
    override fun delete(vararg records: M) = records.onEach { it.markAsSoftDeleted() }
        .let { super.delete(*records) }


    /** Soft delete */
    fun softDelete(vararg records: M) = records
        .onEach { it.markAsSoftDeleted() }
        .let { update(*it) }
}

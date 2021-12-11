package io.taff.exposed.extensions.tables.traits

import io.taff.exposed.extensions.models.SoftDeletableModel
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant

/**
 * Soft delete behavior applicable to tables.
 *
 * @param ID The model id type.
 * @param M The model.
 * @param T The underlying exposed table concrete type.
 */
interface SoftDeletableTableTrait<ID : Comparable<ID>, M : SoftDeletableModel<ID>, T>
    : ModelMappingTableTrait<ID, M, T> where T : IdTable<ID>, T : SoftDeletableTableTrait<ID, M, T>  {

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
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M, vararg skip: Column<*>) {
        if (self().softDeletedAt !in skip) {
            model.softDeletedAt?.let { stmt[softDeletedAt] = it }
        }
        super.appendBaseStatementValues(stmt, model, *skip)
    }

    /** Set tenant Id on models loaded frm the DB */
    override fun toModel(row: ResultRow) = super.toModel(row)
        .also { it.softDeletedAt = row[softDeletedAt] }

    /** Hard delete */
    override fun delete(vararg models: M) = models.onEach { it.markAsSoftDeleted() }
        .let { super.delete(*models) }


    /** Soft delete */
    fun softDelete(vararg models: M) = models
        .onEach { it.markAsSoftDeleted() }
        .let { update(*it) }
}

package io.taff.hephaestus.persistence.tables.traits

import io.taff.hephaestus.persistence.models.DestroyableModel
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Soft delete behavior applicable to tables.
 *
 * @param ID The model id type.
 * @param M The model.
 * @param T The underlying exposed table concrete type.
 */
interface DestroyableTableTrait<ID : Comparable<ID>, M : DestroyableModel<ID>, T >
    : ModelMappingTableTrait<ID, M, T> where T : IdTable<ID>, T : ModelMappingTableTrait<ID, M, T>  {

    val destroyedAt: Column<Instant?>

    /**
     * Returns a copy of this table scoped to destroyed records only.
     */
    fun destroyed() : T

    /**
     * Returns a copy of this table scoped to both live and destroyed records.
     */
    fun includingDestroyed() : T

    /** populate the insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M) {
        model.destroyedAt?.let { stmt[destroyedAt] = it }
        super.appendBaseStatementValues(stmt, model)
    }

    /** Set tenant Id on models loaded frm the DB */
    override fun toModel(row: ResultRow) = super.toModel(row)
        .also { it.destroyedAt = row[destroyedAt] }

    /** Hard delete */
    override fun delete(vararg models: M) = models.onEach { it.markAsDestroyed() }
        .let { super.delete(*models) }


    /** Soft delete */
    fun destroy(vararg models: M) = models
        .onEach { it.markAsDestroyed() }
        .let { update(*it) }
}

package io.taff.hephaestus.persistence.tables.traits

import io.taff.hephaestus.persistence.models.DestroyableModel
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime

interface DestroyableModelTableTrait<M : DestroyableModel, T : UUIDTable>
    : ModelMappingTableTrait<M, T> {

    val destroyedAt: Column<OffsetDateTime?>

    /** populate the insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M) {
        model.destroyedAt?.let { stmt[destroyedAt] = it }
        super.appendBaseStatementValues(stmt, model)
    }

    /** Set tenant Id on models loaded frm the DB */
    override fun toModel(row: ResultRow) = super.toModel(row)
        .also { it.destroyedAt = row[destroyedAt] }

    /** Hard delete */
    override fun delete(vararg models: M) = super
        .delete(*models)
        .onEach { it.markAsDestroyed() }

    /** Soft delete */
    fun destroy(transaction: Transaction, vararg models: M) = models
        .onEach { it.markAsDestroyed() }
        .let { update(transaction, *it) }
}
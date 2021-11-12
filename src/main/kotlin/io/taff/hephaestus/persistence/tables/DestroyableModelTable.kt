package io.taff.hephaestus.persistence.tablestenantId

import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.tables.ModelMappingTable
import io.taff.hephaestus.persistence.models.DestroyableModel
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime

/**
 * A table that supports soft-deletes by setting the `destroyed_at` column on destruction.
 */
abstract class DestroyableModelTable<M : DestroyableModel>(override val name: String) : ModelMappingTable<M>(name) {

    /** The destroyed_at column */
    val destroyedAt = moment("destroyed_at").nullable()

    /** Set tenant Id on models loaded frm the DB */
    override fun fillModel(row: ResultRow, model: M) = model.also {
        it.destroyedAt = row[destroyedAt]
        super.fillModel(row, model)
    }

    /** populate the insert/update statements */
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: M) {
        model.destroyedAt?.let { stmt[destroyedAt] = it }
        super.fillStatement(stmt, model)
    }

    /** Soft delete */
    fun destroy(where: SqlExpressionBuilder.() -> Op<Boolean>) = update({
        Op.build { destroyedAt.isNull() } and where(this)
    }) { stmt -> stmt[destroyedAt] = OffsetDateTime.now() }

    /** Soft delete */
    fun destroy(transaction: Transaction, vararg models: M) = models
        .onEach { it.markAsDestroyed() }
        .let { update(transaction, *it) }

    /** Hard delete */
    override fun delete(vararg models: M) = super
      .delete(*models)
      .onEach { it.markAsDestroyed() }
}
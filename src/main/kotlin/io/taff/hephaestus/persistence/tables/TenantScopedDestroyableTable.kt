package io.taff.hephaestus.persistence.tables

import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.CurrentTenantId
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.models.TenantScopedDestroyableModel
import io.taff.hephaestus.persistence.postgres.moment
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime

/**
 * A table that provides tenant isolation via query methods prefixed with `scoped`. Also supports soft deletes by setting the
 * `destroyed_at` column on destruction
 */
abstract class TenantScopedDestroyableTable<TI, M : TenantScopedDestroyableModel<TI>>(override var name: String) : TenantScopedTable<M>(name) {

    /** The destroyed_at column */
    val destroyedAt = moment("destroyed_at").nullable()

    /** populate the model from a result row */
    override fun fillModel(row: ResultRow, model: M) = super.fillModel(row, model)
        .also { it.destroyedAt = row[destroyedAt] }

    /** populate insert/update statements */
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: M) {
        model.destroyedAt?.let { stmt[destroyedAt] = it }
        super.fillStatement(stmt, model)
    }

    /** Soft delete and raise a TenantError if they don't belong to the current tenant */
    fun destroy(transaction: Transaction, vararg models: M) = models.also {
        validateDestruction(models)
            .onEach { model -> model.markAsDestroyed() }
        return super.update(transaction, *models)
    }

    /** Hard delete the provided records and raise a TenantError if they don't belong to the current tenant */
    override fun delete(vararg models: M) = validateDestruction(models)
            .also { super.delete(*models) }
            .let { models.onEach { it.markAsDestroyed() } }
}
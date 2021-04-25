package io.taff.hephaestus.persistence.tables

import io.taff.hephaestus.persistence.models.DestroyableTenantScopedModel
import io.taff.hephaestus.persistence.postgres.moment
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime

abstract class DestroyableTenantScopedTable<TI, M : DestroyableTenantScopedModel<TI>>(override var name: String) : TenantScopedTable<TI, M>(name) {

    val destroyedAt = moment("destroyed_at").nullable()

    override fun fill(row: ResultRow, model: M) = super.fill(row, model)
        .also { it.destroyedAt = row[destroyedAt] }

    override fun fill(stmt: UpdateBuilder<Int>, model: M) {
        model.destroyedAt?.let { stmt[destroyedAt] = it }
        super.fill(stmt, model)
    }

    /**
     *
     */
    fun <T : ModelAwareTable<*>> destroy(where: SqlExpressionBuilder.() -> Op<Boolean>) = update({
        Op.build { destroyedAt.isNull() } and tenantScope() and where(this)
    }) { stmt -> stmt[destroyedAt] = OffsetDateTime.now() }

    /**
     *
     */
    fun destroy(transaction: Transaction, models: Iterable<M>) = update(transaction, models)
        .onEach { it.markAsDestroyed() }

    /**
     *
     */
    fun destroy(transaction: Transaction, vararg models: M) = update(transaction, models.toList())
        .onEach { it.markAsDestroyed() }

    /**
     *
     */
    override fun delete(models: Iterable<M>) = super.delete(models)
        .onEach { it.markAsDestroyed() }
}
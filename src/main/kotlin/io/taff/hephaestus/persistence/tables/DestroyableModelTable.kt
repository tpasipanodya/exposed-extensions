package io.taff.hephaestus.persistence.tablestenantId

import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.tables.ModelAwareTable
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.TenantScopedTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.OffsetDateTime

abstract class DestroyableModelTable<M : DestroyableModel>(override val name: String) : ModelAwareTable<M>(name) {

    val destroyedAt = moment("destroyed_at").nullable()

    override fun fill(stmt: UpdateBuilder<Int>, model: M) {
        model.destroyedAt?.let { stmt[destroyedAt] = it }
        super.fill(stmt, model)
    }

    /**
    *
    */
    fun destroy(where: SqlExpressionBuilder.() -> Op<Boolean>) = update({
        Op.build { destroyedAt.isNull() } and where(this)
    }) { stmt -> stmt[destroyedAt] = OffsetDateTime.now() }

    /**
     *
     */
    fun destroy(transaction: Transaction, models: Iterable<M>) = models
        .onEach { it.markAsDestroyed() }
        .let { update(transaction, it) }


    /**
     *
     */
    override fun delete(models: Iterable<M>) = super.delete(models)
        .onEach { it.markAsDestroyed() }
}
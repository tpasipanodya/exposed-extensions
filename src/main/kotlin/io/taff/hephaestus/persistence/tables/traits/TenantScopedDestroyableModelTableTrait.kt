package io.taff.hephaestus.persistence.tables.traits

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.UpdateBuilder


interface TenantScopedDestroyableModelTableTrait<M, T : UUIDTable>
    : TenantScopedTableTrait<M, T>, DestroyableModelTableTrait<M, T> where M : TenantScopedModel, M : DestroyableModel {

    /** populate the model from a result row */
    override fun toModel(row: ResultRow) = super<TenantScopedTableTrait>
        .toModel(row)
        .also { it.destroyedAt = row[destroyedAt] }

    /** populate insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M) {
        super<TenantScopedTableTrait>.appendBaseStatementValues(stmt, model)
        super<DestroyableModelTableTrait>.appendBaseStatementValues(stmt, model)
    }

    override fun delete(vararg models: M) = super<TenantScopedTableTrait>.delete(*models)
        .onEach { it.markAsDestroyed() }

    override fun destroy(transaction: Transaction, vararg models: M) =  validateDestruction(models)
        .onEach { it.markAsDestroyed() }
        .let { update(transaction, *it) }
}
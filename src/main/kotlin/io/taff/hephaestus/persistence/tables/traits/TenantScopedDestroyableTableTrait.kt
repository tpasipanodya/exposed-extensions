package io.taff.hephaestus.persistence.tables.traits

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * Tenant Isolation + soft delete behavior applicable to tables.
 *
 * @param ID The model's concrete id type.
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 * @param T The underlying exposed table's concrete type.
 */
interface TenantScopedDestroyableTableTrait<ID : Comparable<ID>, TID: Comparable<TID>, M, T : IdTable<ID>>
    :TenantScopedTableTrait<ID, TID, M, T>,
    DestroyableTableTrait<ID, M, T>
        where M : TenantScopedModel<ID, TID>,
              M : DestroyableModel<ID> {

    /** populate the model from a result row */
    override fun toModel(row: ResultRow) = super<TenantScopedTableTrait>
        .toModel(row)
        .also { it.destroyedAt = row[destroyedAt] }

    /** populate insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M) {
        super<TenantScopedTableTrait>.appendBaseStatementValues(stmt, model)
        super<DestroyableTableTrait>.appendBaseStatementValues(stmt, model)
    }

    override fun delete(vararg models: M) = super<TenantScopedTableTrait>.delete(*models)
        .onEach { it.markAsDestroyed() }

    override fun destroy(transaction: Transaction, vararg models: M) =  validateDestruction(models)
        .onEach { it.markAsDestroyed() }
        .let { update(transaction, *it) }
}
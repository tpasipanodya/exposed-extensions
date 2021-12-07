package io.taff.hephaestus.persistence.tables.traits

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.TenantScopedModel
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * Tenant Isolation + soft delete behavior applicable to tables.
 *
 * @param ID The model's concrete id type.
 * @param TID The concrete tenantId type.
 * @param M The concrete model type.
 * @param T The underlying exposed table's concrete type.
 */
interface TenantScopedDestroyableTableTrait<ID : Comparable<ID>, TID: Comparable<TID>, M, T>
    :TenantScopedTableTrait<ID, TID, M, T>, DestroyableTableTrait<ID, M, T>
        where M : TenantScopedModel<ID, TID>,
              M : DestroyableModel<ID>,
              T : IdTable<ID>,
              T:  DestroyableTableTrait<ID, M, T> {

    /**
     * Returns a version of this table that's scoped to destroyed entities for all tenants.
     *
     * i.e negates the soft delete scope and strips the current tenant scope.
     */
    fun destroyedForAllTenants() : T

    /**
     * Returns a version of this table that's scoped to both live and destroyed records for all tenants.
     *
     * i.e strips all scopes.
     */
    fun liveAndDestroyedForAllTenants() : T

    /**
     * returns a version of this table that's scoped to live records for all tenants.
     *
     * i.e strips the soft delete scope and leaves the current tenant scope intact.
     *
     */
    fun liveForAllTenants() : T

    /** populate the model from a result row */
    override fun toModel(row: ResultRow) = super<TenantScopedTableTrait>
        .toModel(row)
        .also { it.destroyedAt = row[destroyedAt] }

    /** populate insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M, vararg skip: Column<*>) {
        super<TenantScopedTableTrait>.appendBaseStatementValues(stmt, model, *skip)
        super<DestroyableTableTrait>.appendBaseStatementValues(stmt, model, *skip)
    }

    override fun delete(vararg models: M) : Boolean = super<TenantScopedTableTrait>.delete(*models)
        .also { modelsDeleted -> if (modelsDeleted) models.forEach { it.markAsDestroyed() } }


    override fun destroy(vararg models: M) =  validateDestruction(models)
        .onEach(DestroyableModel<ID>::markAsDestroyed)
        .let(::update)
        .also { modelsDestroyed -> if (!modelsDestroyed) models.forEach(DestroyableModel<ID>::markAsLive) }
}

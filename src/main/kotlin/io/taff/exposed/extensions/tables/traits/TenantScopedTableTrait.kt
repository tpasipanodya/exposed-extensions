package io.taff.exposed.extensions.tables.traits

import io.taff.exposed.extensions.CurrentTenantId
import io.taff.exposed.extensions.TenantError
import io.taff.exposed.extensions.isNull
import io.taff.exposed.extensions.models.TenantScopedModel
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * Tenant Isolation behavior applicable to tables.
 *
 * @param ID The model's concrete id type.
 * @param TID The concrete tenantId type.
 * @param M The model's concrete type.
 * @param T The underlying exposed table's concrete type.
 */
interface TenantScopedTableTrait<ID : Comparable<ID>, TID: Comparable<TID>, M : TenantScopedModel<ID, TID>,
        T : IdTable<ID>> : ModelMappingTableTrait<ID, M, T> {

    /** The Tenant id column. */
    val tenantId: Column<TID>

    /** Returns a view on all records across tenants. Identical to stripDefaultScope */
    fun forAllTenants() : T

    /** Set tenant Id on models loaded frm the DB */
    override fun toModel(row: ResultRow) = super.toModel(row)
        .also { it.tenantId = row[tenantId] }


    /** Set tenant Id on insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M, vararg skip: Column<*>) {
        if (tenantId !in skip) {
            (CurrentTenantId.get() as TID?)
                ?.let { safeTenantId ->
                    if (model.tenantId.isNull() || model.tenantId == safeTenantId) {
                        stmt[tenantId] = safeTenantId
                        model.tenantId = safeTenantId
                    } else throw TenantError("Model ${model.id} can't be persisted because it doesn't belong to the current tenant.")
                } ?: throw TenantError("Model ${model.id} can't be persisted because There's no current tenant Id set.")
        }
        super.appendBaseStatementValues(stmt, model, *skip)
    }

    /** Hard delete the provided records and raise a TenantError if they don't belong to the current tenant */
    override fun delete(vararg models: M) = validateDestruction(models)
        .let { super.delete(*models) }

    /** Where clause for tenant isolation */
    fun SqlExpressionBuilder.currentTenantScope() = (CurrentTenantId.get() as TID?)
        ?.let { safeTenantId -> tenantId eq safeTenantId }
        ?: tenantId.isNull()

    /** verify that a delete/softDelete won't violate tenant isolation */
    fun validateDestruction(models: Array<out M>) = models.also {
        if (CurrentTenantId.get().isNull()) throw TenantError("Cannot delete models because there is no CurrentTenantId.")

        models.any { model -> model.tenantId != CurrentTenantId.get() }
            .also { belongsToOtherTenant ->
                if (belongsToOtherTenant) throw TenantError("Cannot delete models because they belong to a different tenant.")
            }
    }
}

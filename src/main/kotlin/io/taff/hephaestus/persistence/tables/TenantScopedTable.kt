package io.taff.hephaestus.persistence.tables

import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.CurrentTenantId
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.models.TenantScopedModel
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * A table that provides tenant isolation via query methods prefixed with `scoped`.
 */
abstract class TenantScopedTable<TI, M : TenantScopedModel<TI>>(override var name: String) : ModelAwareTable<M>(name) {

    /** The Tenant id column. */
    abstract val tenantId: Column<TI>

    /** Where clause for tenant isolation */
    fun tenantScope() = Op.build { tenantId eq CurrentTenantId.get() as TI }

    /** Set tenant Id on models loaded frm the DB */
    override fun fill(row: ResultRow, model: M) = model.also {
        it.tenantId = row[tenantId]
        super.fill(row, model)
    }

    /** Set tenant Id on insert/update statements */
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: M) {
        (CurrentTenantId.get() as TI?)
          ?.let { id ->
              if (model.tenantId.isNull() || model.tenantId == id) {
                  stmt[tenantId] = id
                  model.tenantId = id
              } else throw TenantError("Model ${model.id} can't be persisted because it doesn't belong to the current tenant.")
        } ?: throw TenantError("Model ${model.id} can't be persisted because There's no current tenant Id set.")
        super.fillStatement(stmt, model)
    }

    /** Select records belonging to the current tenant */
    fun scopedSelect(query: SqlExpressionBuilder.() -> Op<Boolean>) = select { query() and tenantScope() }

    /** Select records belonging to the current tenant */
    fun scopedSelect() = select { tenantScope() }

    /** Hard delete the provided records and raise a TenantError if they don't belong to the current tenant */
    open fun scopedDelete(vararg models: M) = models
            .also { validateDestruction(it) }
            .let { super.delete(*models) }

    /** Hard delete records belonging to the current tenant */
    fun scopedDelete(where: SqlExpressionBuilder.() -> Op<Boolean>) = deleteWhere { tenantScope() and where(this) }

    /** verify that a delete/destroy won't violate tenant isolation */
    protected fun validateDestruction(it: Array<out M>) {
        if (CurrentTenantId.get().isNull()) throw TenantError("Cannot destroy models because there is no CurrentTenantId.")

        it.any { model -> model.tenantId != CurrentTenantId.get() }
                .also { belongsToOtherTenant ->
                    if (belongsToOtherTenant) throw TenantError("Cannot destroy models because they belong to a different tenant.")
                }
    }
}
